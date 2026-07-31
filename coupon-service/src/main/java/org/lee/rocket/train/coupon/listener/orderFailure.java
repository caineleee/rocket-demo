package org.lee.rocket.train.coupon.listener;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.lee.rocket.train.api.ICouponService;
import org.lee.rocket.train.service.entity.Coupon;
import org.lee.rocket.train.service.entity.MQEntity;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 订单失败监听器 —— 回退优惠券状态
 *
 * 【修复内容】
 * 1. fastjson1 (com.alibaba.fastjson.JSON) → fastjson2：统一 JSON 库，修复 fastjson1 安全漏洞
 * 2. new String(body, "UTF-8") → StandardCharsets.UTF_8
 * 3. 回退更新加 WHERE is_used = true 条件：只回退已使用的券，防止重复回退（幂等）
 * 4. 删除重复的 body 解析（原 else 分支又 new String 了一次）
 * 5. catch 块 log.info → log.error 并传入异常对象
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
    private ICouponService couponService;

    @Override
    public void onMessage(MessageExt messageExt) {
        String msgId = messageExt.getMsgId();
        String tags = messageExt.getTags();
        String keys = messageExt.getKeys();
        try {
            String body = new String(messageExt.getBody(), StandardCharsets.UTF_8);
            log.info("接收到订单失败消息: msgId: {}, tags: {}, keys: {}, body: {}", msgId, tags, keys, body);

            // 回退优惠券状态
            MQEntity mqEntity = JSON.parseObject(body, MQEntity.class);
            if (mqEntity != null && mqEntity.getCouponId() != null) {
                Long couponId = mqEntity.getCouponId();
                // 回退优惠券状态：加 WHERE is_used = true 条件
                // 只回退已使用的券，靠 DB 行锁保证幂等性（重复回退时 update 0 行）
                @SuppressWarnings("null")
                boolean success = couponService.update(
                        null,
                        new LambdaUpdateWrapper<Coupon>()
                                .set(Coupon::getIsUsed, Boolean.FALSE)
                                .set(Coupon::getUsedTime, null)
                                .set(Coupon::getOrderId, null)
                                .eq(Coupon::getCouponId, couponId)
                                .eq(Coupon::getIsUsed, true)
                );
                if (success) {
                    log.info("订单失败消息, 优惠券回退成功: msgId: {}, tags: {}, keys: {}", msgId, tags, keys);
                } else {
                    // update 0 行：券不存在或未使用（可能已回退过），视为幂等成功
                    log.info("订单失败消息, 优惠券无需回退(不存在或已回退): msgId: {}, tags: {}, keys: {}", msgId, tags, keys);
                }
            } else {
                // 【修复】删除重复 body 解析，直接用已有 body 变量
                log.info("订单失败消息, 优惠券回退无相关数据: msgId: {}, tags: {}, keys: {}, body: {}", msgId, tags, keys, body);
            }
        } catch (Exception e) {
            // 【修复】log.info → log.error 并传入异常对象
            log.error("订单失败消息, 优惠券回退处理异常: msgId: {}, tags: {}, keys: {}", msgId, tags, keys, e);
            throw new RuntimeException(e);
        }
    }
}
