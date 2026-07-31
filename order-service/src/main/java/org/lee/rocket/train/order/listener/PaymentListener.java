package org.lee.rocket.train.order.listener;

import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.lee.rocket.train.api.IOrdersService;
import org.lee.rocket.train.common.constant.code.ResultCode;
import org.lee.rocket.train.common.constant.status.OrderStatus;
import org.lee.rocket.train.common.exception.CastException;
import org.lee.rocket.train.service.entity.Order;
import org.lee.rocket.train.service.entity.Payment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * 监听支付服务发起的 MQ 消息，修改订单状态为已支付（实际置为已确认 CONFIRMED）
 *
 * 【修复内容】
 * 1. catch (UnsupportedEncodingException) → catch (Exception)：JSON 解析/DB 异常/CastException 不再逃逸
 * 2. new String(body, "UTF-8") → StandardCharsets.UTF_8
 * 3. 失败时 log.error 并传入异常对象
 * 4. order.getOrderStatus() NPE 防御：改用 OrderStatus.CONFIRMED.getCode().equals(...) 避免 Integer 为 null 拆箱 NPE
 *
 * @author lihongliang
 */
@Slf4j
@Component
// 监听支付成功 MQ 消息，更新订单状态为已支付
// topic/consumerGroup 引用 application.yml 中已定义的属性：
//   mq.topics.payment=pay-topic        （与 pay-service 发送端 rocketmq.topics.pay 同值，保证收发一致）
//   mq.payment.consumer.group=payment-consumer-group
// 此前误用 ${rocketmq.order.topic}/${rocketmq.order.consumer.group}（未定义），导致占位符无法解析、消费者启动失败
@RocketMQMessageListener(topic = "${mq.topics.payment}",
        consumerGroup = "${mq.payment.consumer.group}", messageModel = MessageModel.BROADCASTING)
public class PaymentListener implements RocketMQListener<MessageExt> {

    @Resource
    private IOrdersService ordersService;

    @Override
    public void onMessage(MessageExt messageExt) {
        String msgId = messageExt.getMsgId();
        String tags = messageExt.getTags();
        String keys = messageExt.getKeys();
        try {
            // 用 StandardCharsets.UTF_8 避免抛 UnsupportedEncodingException
            String body = new String(messageExt.getBody(), StandardCharsets.UTF_8);
            log.info("接受到支付成功 mq 消息, msgId:{}, tags:{}, 业务Id:{} , 消息内容:{}", msgId, tags, keys, body);

            Payment payment = JSON.parseObject(body, Payment.class);
            // 查询订单数据
            Order order = ordersService.lambdaQuery().eq(Order::getOrderId, payment.getOrderId()).one();
            if (order == null) {
                log.error("PaymentListener 订单不存在，订单ID: {}", payment.getOrderId());
                return;
            }
            // 更改订单状态（原注释写"已支付"但实际置为"已确认"，保留原逻辑仅替换枚举）
            // 【修复】NPE 防御：order.getOrderStatus() 可能为 null，改用常量在前调用 equals
            if (Objects.equals(order.getOrderStatus(), OrderStatus.CONFIRMED.getCode())) {
                // 【修复】原用 ShopCode.ORDER_CONFIRM(状态码) 当响应码抛，改用 ORDER_STATUS_UPDATE_FAIL 响应码
                CastException.cast(ResultCode.ORDER_STATUS_UPDATE_FAIL);
            }
            order.setOrderStatus(OrderStatus.CONFIRMED.getCode());
            // 更新数据库
            ordersService.updateById(order);
            log.info("更改订单状态为已支付, 订单ID: {}, 业务ID: {}", order.getOrderId(), keys);
        } catch (Exception e) {
            // 【修复】catch Exception 而非 UnsupportedEncodingException，并记录堆栈
            log.error("支付成功消息处理失败: msgId: {}, tags: {}, keys: {}", msgId, tags, keys, e);
            throw new RuntimeException(e);
        }

    }
}
