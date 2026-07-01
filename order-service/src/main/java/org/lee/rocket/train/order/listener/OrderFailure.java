package org.lee.rocket.train.order.listener;

import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.lee.rocket.train.api.IOrdersService;
import org.lee.rocket.train.common.constant.ShopCode;
import org.lee.rocket.train.service.entity.MQEntity;
import org.lee.rocket.train.service.entity.Order;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;

/**
 * @ClassName OrderFailure
 * @Description
 * @Author lihongliang
 * @Date 2026/7/1 17:26
 * @Version 1.0
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "${mq.topics.order-failure}",
        consumerGroup = "${rocketmq.consumer.order-failure.group}",
        messageModel = MessageModel.BROADCASTING) // 广播模式
public class OrderFailure implements RocketMQListener<MessageExt> {

    @Resource
    private IOrdersService orderService;

    @Override
    public void onMessage(MessageExt messageExt) {
        // 解析消息内容
        String msgId = messageExt.getMsgId();
        String tags = messageExt.getTags();
        String keys = messageExt.getKeys();
        try {
            String body = new String(messageExt.getBody(), "UTF-8");
            log.info("接收到订单失败消息: msgId: {}, tags: {}, keys: {}, body: {}", msgId, tags, keys, body);

            MQEntity mqEntity = JSONObject.parseObject(body, MQEntity.class);
            // 查询订单
            Order order = orderService.lambdaQuery().eq(Order::getOrderId, mqEntity.getOrderId()).one();
            order.setOrderStatus(ShopCode.ORDER_MESSAGE_STATUS_CANCEL.getCode());
            orderService.updateById(order);
            // 更新订单状态为取消

            log.info("订单失败消息, 订单状态处理成功: msgId: {}, tags: {}, keys: {}", msgId, tags, keys);

        } catch (UnsupportedEncodingException e) {
            log.info("订单失败消息, 订单状态处理失败: msgId: {}, tags: {}, keys: {}", msgId, tags, keys);
            throw new RuntimeException(e);
        }

    }
}
