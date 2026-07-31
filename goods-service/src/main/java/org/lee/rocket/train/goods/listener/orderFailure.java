package org.lee.rocket.train.goods.listener;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.lee.rocket.train.api.IGoodsService;
import org.lee.rocket.train.api.IGoodsStocksLogService;
import org.lee.rocket.train.common.constant.code.ResultCode;
import org.lee.rocket.train.common.constant.status.MqMessageStatus;
import org.lee.rocket.train.common.exception.CastException;
import org.lee.rocket.train.service.entity.Goods;
import org.lee.rocket.train.service.entity.GoodsStocksLog;
import org.lee.rocket.train.service.entity.MQEntity;
import org.lee.rocket.train.service.entity.MqConsumerLog;
import org.lee.rocket.train.service.mapper.MqConsumerLogMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * 订单失败监听器 —— 回退商品库存
 *
 * 【修复内容】
 * 1. fastjson1 (com.alibaba.fastjson.JSON) → fastjson2：统一 JSON 库，修复 fastjson1 反序列化安全漏洞
 * 2. 删除 @SuppressWarnings("null")，修复 catch 块 mqConsumerLog NPE（selectByCompositeKey 可能返回 null）
 * 3. consumerStatus/consumerTimes 全部用 Objects.equals / Optional 防 null（Integer 自动拆箱 NPE）
 * 4. new String(body, "UTF-8") → StandardCharsets.UTF_8
 * 5. catch 块 log.info → log.error 并传入异常对象，保留堆栈
 *
 * @author lihongliang
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "${mq.topics.order-failure}",
        consumerGroup = "${rocketmq.consumer.order-failure.group}",
        messageModel = MessageModel.BROADCASTING)
public class orderFailure implements RocketMQListener<MessageExt> {

    @Value("${rocketmq.consumer.order-failure.group}")
    private String consumerGroup;

    @Resource
    private MqConsumerLogMapper mqConsumerLogMapper;

    @Resource
    private IGoodsService goodsService;

    @Resource
    private IGoodsStocksLogService goodsStocksLogService;

    @Override
    public void onMessage(MessageExt messageExt) {
        String msgId = messageExt.getMsgId();
        String tags = messageExt.getTags();
        String keys = messageExt.getKeys();
        try {
            String body = new String(messageExt.getBody(), StandardCharsets.UTF_8);
            log.info("接收到订单失败消息: msgId: {}, tags: {}, keys: {}, body: {}", msgId, tags, keys, body);

            // 查询消息消费记录
            MqConsumerLog mqConsumerLog = mqConsumerLogMapper.selectByCompositeKey(consumerGroup, tags, keys);
            if (mqConsumerLog != null) {
                Integer consumerStatus = mqConsumerLog.getConsumerStatus();
                // 状态 0:处理中, 1:处理成功, 直接返回
                // 【修复】全部用 Objects.equals 防 null（原代码前半段用 Objects.equals 后半段用 .equals）
                if (Objects.equals(consumerStatus, MqMessageStatus.PROCESSING.getCode())
                        || Objects.equals(consumerStatus, MqMessageStatus.SUCCESS.getCode())) {
                    log.info("消息处理状态: {}，tags: {}, keys: {}，直接返回", consumerStatus, tags, keys);
                    return;
                }
                // 状态 2:处理失败
                if (Objects.equals(consumerStatus, MqMessageStatus.FAIL.getCode())) {
                    // 【修复】consumerTimes 是 Integer，DB 为 NULL 时自动拆箱 NPE
                    int consumerTimes = Optional.ofNullable(mqConsumerLog.getConsumerTimes()).orElse(0);
                    if (consumerTimes >= 3) {
                        log.error("消息处理状态: {}，tags: {}, keys: {}，处理失败次数超过上限(3次)，无法处理", consumerStatus, tags, keys);
                        return;
                    }
                    // 乐观锁更新数据：只有当状态仍然是"失败"时才更新
                    int updateRows = mqConsumerLogMapper.update(
                            null,
                            new LambdaUpdateWrapper<MqConsumerLog>()
                                    .set(MqConsumerLog::getConsumerStatus, MqMessageStatus.PROCESSING.getCode())
                                    .set(MqConsumerLog::getConsumerTimes, consumerTimes + 1)
                                    .set(MqConsumerLog::getConsumerTime, LocalDateTime.now())
                                    .eq(MqConsumerLog::getGroupName, mqConsumerLog.getGroupName())
                                    .eq(MqConsumerLog::getMsgTag, mqConsumerLog.getMsgTag())
                                    .eq(MqConsumerLog::getMsgKey, mqConsumerLog.getMsgKey())
                                    .eq(MqConsumerLog::getConsumerStatus, MqMessageStatus.FAIL.getCode())
                    );

                    if (updateRows <= 0) {
                        log.warn("并发修改中,稍后处理: tags={}, keys={}", tags, keys);
                        CastException.cast(ResultCode.MQ_MESSAGE_CONCURRENT_UPDATE_FAIL);
                    }
                }
            } else {
                // 如果消息没有消费过
                MqConsumerLog newMqConsumerLog = new MqConsumerLog();
                newMqConsumerLog.setMsgId(msgId);
                newMqConsumerLog.setGroupName(consumerGroup);
                newMqConsumerLog.setMsgTag(tags);
                newMqConsumerLog.setMsgKey(keys);
                newMqConsumerLog.setMsgBody(body);
                newMqConsumerLog.setConsumerStatus(MqMessageStatus.PROCESSING.getCode());
                newMqConsumerLog.setConsumerTimes(0);
                newMqConsumerLog.setConsumerTime(LocalDateTime.now());
                mqConsumerLogMapper.insert(newMqConsumerLog);
            }

            // 回退库存
            MQEntity mqEntity = JSON.parseObject(body, MQEntity.class);
            Long goodsId = mqEntity.getGoodsId();
            Long orderId = mqEntity.getOrderId();

            // 幂等性检查：如果库存操作日志已存在，说明业务已处理过，直接标记成功
            long existCount = goodsStocksLogService.lambdaQuery()
                    .eq(GoodsStocksLog::getGoodsId, goodsId)
                    .eq(GoodsStocksLog::getOrderId, orderId)
                    .count();
            if (existCount > 0) {
                log.info("库存操作日志已存在，消息已处理过，直接标记成功: goodsId={}, orderId={}", goodsId, orderId);
                mqConsumerLogMapper.update(null, new LambdaUpdateWrapper<MqConsumerLog>()
                        .eq(MqConsumerLog::getGroupName, consumerGroup)
                        .eq(MqConsumerLog::getMsgTag, tags)
                        .eq(MqConsumerLog::getMsgKey, keys)
                        .set(MqConsumerLog::getConsumerStatus, MqMessageStatus.SUCCESS.getCode())
                        .set(MqConsumerLog::getConsumerTime, LocalDateTime.now()));
                return;
            }

            Goods goods = goodsService.lambdaQuery().eq(Goods::getGoodsId, goodsId).one();
            if (goods == null) {
                CastException.cast(ResultCode.GOODS_NO_EXIST);
            }
            goods.setGoodsNumber(goods.getGoodsNumber() + mqEntity.getGoodsNumber());
            goodsService.updateById(goods);

            // 记录库存操作日志
            GoodsStocksLog goodsStocksLog = new GoodsStocksLog();
            goodsStocksLog.setGoodsId(goods.getGoodsId());
            goodsStocksLog.setGoodsNumber(mqEntity.getGoodsNumber());
            goodsStocksLog.setOrderId(mqEntity.getOrderId());
            goodsStocksLog.setLogTime(LocalDateTime.now());
            goodsStocksLogService.save(goodsStocksLog);

            // 设置消息处理状态: 成功
            mqConsumerLogMapper.update(null, new LambdaUpdateWrapper<MqConsumerLog>()
                    .eq(MqConsumerLog::getGroupName, consumerGroup)
                    .eq(MqConsumerLog::getMsgTag, tags)
                    .eq(MqConsumerLog::getMsgKey, keys)
                    .set(MqConsumerLog::getConsumerStatus, MqMessageStatus.SUCCESS.getCode())
                    .set(MqConsumerLog::getConsumerTime, LocalDateTime.now()));

            log.info("回退库存成功 订单:{}, 商品:{}, 用户:{}", mqEntity.getOrderId(), goods.getGoodsName(), mqEntity.getUserId());

        } catch (Exception e) {
            // 【修复】catch 块 NPE 防御：selectByCompositeKey 可能返回 null（如初始 insert 失败）
            MqConsumerLog logRecord = mqConsumerLogMapper.selectByCompositeKey(consumerGroup, tags, keys);
            if (logRecord != null) {
                int currentTimes = Optional.ofNullable(logRecord.getConsumerTimes()).orElse(0);
                mqConsumerLogMapper.update(null, new LambdaUpdateWrapper<MqConsumerLog>()
                        .set(MqConsumerLog::getConsumerStatus, MqMessageStatus.FAIL.getCode())
                        .set(MqConsumerLog::getConsumerTimes, currentTimes + 1)
                        .eq(MqConsumerLog::getGroupName, consumerGroup)
                        .eq(MqConsumerLog::getMsgTag, tags)
                        .eq(MqConsumerLog::getMsgKey, keys));
            }
            // 【修复】log.info → log.error，传入异常对象保留堆栈
            log.error("订单失败消息处理异常: msgId: {}, tags: {}, keys: {}", msgId, tags, keys, e);
            throw new RuntimeException(e);
        }
    }
}
