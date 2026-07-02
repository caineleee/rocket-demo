package org.lee.rocket.train.order.listener;

import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.lee.rocket.train.api.IOrdersService;
import org.lee.rocket.train.common.constant.ShopCode;
import org.lee.rocket.train.common.exception.CastException;
import org.lee.rocket.train.service.entity.Order;
import org.lee.rocket.train.service.entity.Payment;
import org.springframework.stereotype.Component;

import java.io.UnsupportedEncodingException;

/**
 * @ClassName PaymentListener
 * @Description 监听支付服务发起mq 消息, 修改订单状态为已支付
 * @Author lihongliang
 * @Date 2026/7/2 21:27
 * @Version 1.0
 */
@Slf4j
@Component
@RocketMQMessageListener(topic = "${rocketmq.order.topic}",
        consumerGroup = "${rocketmq.order.consumer.group}",messageModel = MessageModel.BROADCASTING)
public class PaymentListener implements RocketMQListener<MessageExt> {

    @Resource
    private IOrdersService ordersService;

    @Override
    public void onMessage(MessageExt messageExt) {
        try {
            // 解析消息
            String body = new String(messageExt.getBody(), "UTF-8");
            log.info("接受到支付成功 mq 消息, 业务Id:{} , 消息内容:{}", messageExt.getKeys(), body);

            Payment payment = JSON.parseObject(body, Payment.class);
            // 查询订单数据
            Order order = ordersService.lambdaQuery().eq(Order::getOrderId, payment.getOrderId()).one();
            if (order == null) {
                log.error("PaymentListener 订单不存在，订单ID: {}", payment.getOrderId());
                return;
            }
            // 更改订单状态:已支付
            if (order.getOrderStatus().equals(ShopCode.ORDER_CONFIRM.getCode())) {
                CastException.cast(ShopCode.ORDER_CONFIRM);
            }
            order.setOrderStatus(ShopCode.ORDER_CONFIRM.getCode());
            // 更新数据库
            ordersService.updateById(order);
            log.info("更改订单状态为已支付, 订单ID: {}, 业务ID: {}", order.getOrderId(), messageExt.getKeys());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

    }
}
