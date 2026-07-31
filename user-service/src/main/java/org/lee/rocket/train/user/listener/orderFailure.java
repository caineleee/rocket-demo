package org.lee.rocket.train.user.listener;

import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.lee.rocket.train.api.IUserService;
import org.lee.rocket.train.common.constant.status.UserMoneyLogType;
import org.lee.rocket.train.service.entity.MQEntity;
import org.lee.rocket.train.service.entity.UserMoneyLog;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

/**
 * 订单失败监听器 —— 回退用户余额
 *
 * 【修复内容】
 * 1. catch (UnsupportedEncodingException) → catch (Exception)：
 *    原来只捕获编码异常（而 UTF-8 由 JLS 保证不会抛），JSON 解析失败、DB 异常、CastException
 *    全部逃逸导致消息被 ACK 丢失；改为 catch (Exception) 后抛 RuntimeException 触发重试
 * 2. new String(body, "UTF-8") → StandardCharsets.UTF_8：去掉无用的 UnsupportedEncodingException
 * 3. 失败日志 log.info → log.error 并传入异常对象，保留堆栈用于排查
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

    @Resource
    private IUserService userService;

    @Override
    public void onMessage(MessageExt messageExt) {
        String msgId = messageExt.getMsgId();
        String tags = messageExt.getTags();
        String keys = messageExt.getKeys();
        try {
            // 用 StandardCharsets.UTF_8 避免抛 UnsupportedEncodingException
            String body = new String(messageExt.getBody(), StandardCharsets.UTF_8);
            log.info("接收到订单失败消息: msgId: {}, tags: {}, keys: {}, body: {}", msgId, tags, keys, body);

            MQEntity mqEntity = JSONObject.parseObject(body, MQEntity.class);

            // 调用 service 层方法，回退余额
            if (mqEntity != null
                    && mqEntity.getUserMoney() != null
                    && !mqEntity.getUserMoney().equals(0L)) {

                UserMoneyLog userMoneyLog = new UserMoneyLog()
                        .setOrderId(mqEntity.getOrderId())
                        .setUserId(mqEntity.getUserId())
                        .setUseMoney(mqEntity.getUserMoney())
                        .setMoneyLogType(UserMoneyLogType.REFUND.getCode())
                        .setCreateTime(LocalDateTime.now());
                // 数据库回退
                userService.updateMoneyPaid(userMoneyLog);

                log.info("订单失败消息, 余额回退成功: msgId: {}, tags: {}, keys: {}", msgId, tags, keys);
            } else {
                log.info("订单失败消息, 余额为0, 不需要回退: msgId: {}, tags: {}, keys: {}", msgId, tags, keys);
            }

        } catch (Exception e) {
            // 【修复】catch Exception 而非 UnsupportedEncodingException
            // BROADCASTING 模式下 broker 不重试，抛 RuntimeException 让应用层感知失败
            log.error("订单失败消息, 余额回退失败: msgId: {}, tags: {}, keys: {}", msgId, tags, keys, e);
            throw new RuntimeException(e);
        }
    }
}
