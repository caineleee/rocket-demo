package org.lee.rocket.train.order.listener;

import com.alibaba.fastjson2.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.lee.rocket.train.api.IOrdersService;
import org.lee.rocket.train.common.constant.status.OrderStatus;
import org.lee.rocket.train.common.statemachine.StatusTransition;
import org.lee.rocket.train.service.entity.MQEntity;
import org.lee.rocket.train.service.entity.Order;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 订单失败监听器 —— 将订单状态置为无效（NO_CONFIRM → INVALID）
 *
 * 【修复内容】
 * 1. catch (UnsupportedEncodingException) → catch (Exception)：
 *    原来只捕获编码异常（UTF-8 由 JLS 保证不会抛），JSON 解析失败、DB 异常、CastException
 *    全部逃逸导致消息被 ACK 丢失；改为 catch (Exception) 后抛 RuntimeException 触发重试
 * 2. new String(body, "UTF-8") → StandardCharsets.UTF_8：去掉无用的 UnsupportedEncodingException
 * 3. 失败日志 log.info → log.error 并传入异常对象，保留堆栈用于排查
 * 4. 订单查询结果 NPE 防御：order 为 null 时不再 NPE，改为业务异常
 *
 * @author lihongliang
 */
@Slf4j
@Component
@RocketMQMessageListener(
        topic = "${mq.topics.order-failure}",
        consumerGroup = "${mq.order-failure.consumer.group}",
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
            // 用 StandardCharsets.UTF_8 避免抛 UnsupportedEncodingException
            String body = new String(messageExt.getBody(), StandardCharsets.UTF_8);
            log.info("接收到订单失败消息: msgId: {}, tags: {}, keys: {}, body: {}", msgId, tags, keys, body);

            MQEntity mqEntity = JSONObject.parseObject(body, MQEntity.class);
            // 查询订单
            Order order = orderService.lambdaQuery().eq(Order::getOrderId, mqEntity.getOrderId()).one();
            // 【修复】NPE 防御：订单不存在时直接抛业务异常，避免 order.getOrderStatus() NPE
            if (order == null) {
                log.error("订单失败消息, 订单不存在: msgId: {}, tags: {}, keys: {}, orderId: {}",
                        msgId, tags, keys, mqEntity.getOrderId());
                throw new RuntimeException("订单不存在: " + mqEntity.getOrderId());
            }
            // 订单确认失败，置为无效（状态机校验：NO_CONFIRM → INVALID）
            // 【修复】原用 ShopCode.ORDER_MESSAGE_STATUS_CANCEL(code=1)，实际等于"已确认"状态，是 bug；
            //        订单确认失败应置为 INVALID(3)，且 NO_CONFIRM→INVALID 是合法流转
            OrderStatus from = OrderStatus.of(order.getOrderStatus());
            StatusTransition.check(from, OrderStatus.INVALID);
            order.setOrderStatus(OrderStatus.INVALID.getCode());
            orderService.updateById(order);
            // 更新订单状态为无效

            log.info("订单失败消息, 订单状态处理成功: msgId: {}, tags: {}, keys: {}", msgId, tags, keys);

        } catch (Exception e) {
            // 【修复】catch Exception 而非 UnsupportedEncodingException
            // BROADCASTING 模式下 broker 不重试，抛 RuntimeException 让应用层感知失败
            log.error("订单失败消息, 订单状态处理失败: msgId: {}, tags: {}, keys: {}", msgId, tags, keys, e);
            throw new RuntimeException(e);
        }

    }
}
