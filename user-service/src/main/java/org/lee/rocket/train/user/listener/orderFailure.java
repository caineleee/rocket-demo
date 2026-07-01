package org.lee.rocket.train.user.listener;

import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.lee.rocket.train.api.IUserService;
import org.lee.rocket.train.common.constant.ShopCode;
import org.lee.rocket.train.service.entity.MQEntity;
import org.lee.rocket.train.service.entity.UserMoneyLog;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;

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

    @Resource
    private IUserService userService;

    @Override
    public void onMessage(MessageExt messageExt) {
        // 解析消息
        String msgId = messageExt.getMsgId();
        String tags = messageExt.getTags();
        String keys = messageExt.getKeys();
        String body = null;
        try {
            body = new String(messageExt.getBody(), "UTF-8");
            log.info("接收到订单失败消息: msgId: {}, tags: {}, keys: {}, body: {}", msgId, tags, keys, body);

            MQEntity mqEntity = JSONObject.parseObject(body, MQEntity.class);

            // 调用 service 层方法, 回退余额
            if (mqEntity != null
                    && mqEntity.getUserMoney() != null
                    && !mqEntity.getUserMoney().equals(0L)) {

                UserMoneyLog userMoneyLog = new UserMoneyLog()
                        .setOrderId(mqEntity.getOrderId())
                        .setUserId(mqEntity.getUserId()).setUseMoney(mqEntity.getUserMoney())
                        .setMoneyLogType(ShopCode.USER_MONEY_REFUND.getCode())
                        .setCreateTime(LocalDateTime.now());
                // 数据库回退
                userService.updateMoneyPaid(userMoneyLog);

                log.info("订单失败消息, 余额回退成功: msgId: {}, tags: {}, keys: {}, body: {}", msgId, tags, keys, body);
            } else {
                log.info("订单失败消息, 余额为0 ,不需要回退: msgId: {}, tags: {}, keys: {}, body: {}", msgId, tags, keys, body);
            }

        } catch (UnsupportedEncodingException e) {
            log.info("订单失败消息, 余额回退失败: msgId: {}, tags: {}, keys: {}", msgId, tags, keys);
            throw new RuntimeException(e);
        }



    }
}
