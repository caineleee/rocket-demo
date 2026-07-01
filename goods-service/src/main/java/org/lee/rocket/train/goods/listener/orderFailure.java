package org.lee.rocket.train.goods.listener;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.lee.rocket.train.api.IGoodsService;
import org.lee.rocket.train.api.IGoodsStocksLogService;
import org.lee.rocket.train.common.constant.ShopCode;
import org.lee.rocket.train.common.exception.CastException;
import org.lee.rocket.train.service.entity.Goods;
import org.lee.rocket.train.service.entity.GoodsStocksLog;
import org.lee.rocket.train.service.entity.MQEntity;
import org.lee.rocket.train.service.entity.MqConsumerLog;
import org.lee.rocket.train.service.mapper.MqConsumerLogMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * @ClassName orderFailure
 * @Description 订单失败监听器
 * @Author lihongliang
 * @Date 2026/6/29 15:25
 * @Version 1.0
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "${mq.topics.order-failure}",
        consumerGroup = "${rocketmq.consumer.order-failure.group}",
        messageModel = MessageModel.BROADCASTING) // 广播模式
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
        // 解析消息内容
        String msgId = messageExt.getMsgId();
        String tags = messageExt.getTags();
        String keys = messageExt.getKeys();
        try {

            String body = new String(messageExt.getBody(), "UTF-8");

            log.info("接收到订单失败消息: msgId: {}, tags: {}, keys: {}, body: {}", msgId, tags, keys, body);

            // 查询消息消费记录
            MqConsumerLog mqConsumerLog = mqConsumerLogMapper.selectByCompositeKey(consumerGroup, tags, keys);
            if (mqConsumerLog != null) {
                // 如果消息消费过, 获得消息处理状态
                Integer consumerStatus = mqConsumerLog.getConsumerStatus();
                // 状态 0:处理中, 1:处理成功, 直接返回
                if (Objects.equals(consumerStatus, ShopCode.MQ_MESSAGE_STATUS_PROCESSING.getCode())
                        || consumerStatus.equals(ShopCode.MQ_MESSAGE_STATUS_SUCCESS.getCode())) {
                    log.info("消息处理状态: {}，tags: {}, keys: {}，直接返回", consumerStatus, tags, keys);
                    return;
                }
                // 状态 2:处理失败
                if (consumerStatus.equals(ShopCode.MQ_MESSAGE_STATUS_FAIL.getCode())) {
                    Integer consumerTimes = mqConsumerLog.getConsumerTimes();
                    if (consumerTimes > 3) {
                        log.error("消息处理状态: {}，tags: {}, keys: {}，处理失败次数超过上限(3次)，无法处理", consumerStatus, tags, keys);
                        // 注意：超过上限时，return 让 MQ 停止投递，转人工排查
                        return;
                    }
                    // 乐观锁更新数据：只有当状态仍然是"失败"时才更新
                    int updateRows = mqConsumerLogMapper.update(
                            null,
                            new LambdaUpdateWrapper<MqConsumerLog>()
                                    // update 字段
                                    .set(MqConsumerLog::getConsumerStatus, ShopCode.MQ_MESSAGE_STATUS_PROCESSING.getCode())
                                    .set(MqConsumerLog::getConsumerTimes, mqConsumerLog.getConsumerTimes() + 1) // 失败次数加1
                                    .set(MqConsumerLog::getConsumerTime, LocalDateTime.now())
                                    // 指定联合索引
                                    .eq(MqConsumerLog::getGroupName, mqConsumerLog.getGroupName())
                                    .eq(MqConsumerLog::getMsgTag, mqConsumerLog.getMsgTag())
                                    .eq(MqConsumerLog::getMsgKey, mqConsumerLog.getMsgKey())
                                    // 乐观锁条件
                                    .eq(MqConsumerLog::getConsumerStatus, ShopCode.MQ_MESSAGE_STATUS_FAIL.getCode())
                    );

                    if (updateRows <= 0) {
                        log.warn("并发修改中,稍后处理: tags={}, keys={}", tags, keys);
                        CastException.cast(ShopCode.MQ_MESSAGE_CONCURRENT_UPDATE_FAIL);
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
                newMqConsumerLog.setConsumerStatus(ShopCode.MQ_MESSAGE_STATUS_PROCESSING.getCode());
                newMqConsumerLog.setConsumerTimes(0);
                newMqConsumerLog.setConsumerTime(LocalDateTime.now());
                // 插入消息处理记录到数据库
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
                        .set(MqConsumerLog::getConsumerStatus, ShopCode.MQ_MESSAGE_STATUS_SUCCESS.getCode())
                        .set(MqConsumerLog::getConsumerTime, LocalDateTime.now()));
                return;
            }

            Goods goods = goodsService.lambdaQuery().eq(Goods::getGoodsId, goodsId).one();
            if (goods == null) {
                CastException.cast(ShopCode.GOODS_NO_EXIST);
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

                            .set(MqConsumerLog::getConsumerStatus, ShopCode.MQ_MESSAGE_STATUS_SUCCESS.getCode())
                            .set(MqConsumerLog::getConsumerTime, LocalDateTime.now()));

            log.info("回退库存成功 订单:{}, 商品:{}, 用户:{}", mqEntity.getOrderId(), goods.getGoodsName(), mqEntity.getUserId());

        } catch (Exception e) {
            // 更新消息处理状态为失败
            MqConsumerLog mqConsumerLog = mqConsumerLogMapper.selectByCompositeKey(consumerGroup, tags, keys);
            mqConsumerLogMapper.update(null, new LambdaUpdateWrapper<MqConsumerLog>()
                    .set(MqConsumerLog::getConsumerStatus, ShopCode.MQ_MESSAGE_STATUS_FAIL.getCode())
                    .set(MqConsumerLog::getConsumerTimes, mqConsumerLog.getConsumerTimes() + 1)
                    .eq(MqConsumerLog::getGroupName, consumerGroup)
                    .eq(MqConsumerLog::getMsgTag, tags)
                    .eq(MqConsumerLog::getMsgKey, keys));

            log.info("订单失败消息处理异常: msgId: {}, tags: {}, keys: {}, body: {}", msgId, tags, keys);

            throw new RuntimeException(e);        }
    }
}
