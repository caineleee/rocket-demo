package org.lee.rocket.train.coupon.listener;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.lee.rocket.train.api.ICouponService;
import org.lee.rocket.train.common.constant.ShopCode;
import org.lee.rocket.train.common.exception.CastException;
import org.lee.rocket.train.service.entity.Coupon;
import org.lee.rocket.train.service.entity.MQEntity;
import org.springframework.stereotype.Component;


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
    private ICouponService couponService;

    @Override
    public void onMessage(MessageExt messageExt) {
        // 解析消息内容
        String msgId = messageExt.getMsgId();
        String tags = messageExt.getTags();
        String keys = messageExt.getKeys();
        try {

            String body = new String(messageExt.getBody(), "UTF-8");
            log.info("接收到订单失败消息: msgId: {}, tags: {}, keys: {}, body: {}", msgId, tags, keys, body);
            // 回退优惠券状态
            MQEntity mqEntity = JSON.parseObject(body, MQEntity.class);
            if (mqEntity != null && mqEntity.getCouponId() != null) {
                Long couponId = mqEntity.getCouponId();
                Coupon coupon = couponService.lambdaQuery().eq(Coupon::getCouponId, couponId).one();
                if (coupon == null) {
                    CastException.cast(ShopCode.COUPON_NO_EXIST);
                }
                // 回退优惠券状态 必须使用update() 不能使用updateById() 否则 null value 无法被更新
                couponService.update(
                        null,
                        new LambdaUpdateWrapper<Coupon>()
                                .set(Coupon::getIsUsed, Boolean.FALSE)
                                .set(Coupon::getUsedTime, null)
                                .set(Coupon::getOrderId, null)
                                .eq(Coupon::getCouponId, couponId)
                );
                // 设置消息处理状态: 成功
                log.info("订单失败消息, 优惠券回退处理成功: msgId: {}, tags: {}, keys: {}", msgId, tags, keys);
            } else {
                String bodyStr = new String(messageExt.getBody(), "UTF-8");
                log.error("订单失败消息, 优惠券回退 DB 没有相关数据: msgId: {}, tags: {}, keys: {}, body{}", msgId, tags, keys, bodyStr);
            }
        } catch (Exception e) {
            // 更新消息处理状态为失败
            log.info("订单失败消息, 优惠券回退处理异常: msgId: {}, tags: {}, keys: {}", msgId, tags, keys);
            throw new RuntimeException(e);
        }
    }
}
