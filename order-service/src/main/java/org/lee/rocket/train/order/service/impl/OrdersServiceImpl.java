package org.lee.rocket.train.order.service.impl;


import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.exception.RemotingException;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.lee.rocket.train.common.constant.code.ResultCode;
import org.lee.rocket.train.common.constant.status.CouponStatus;
import org.lee.rocket.train.common.constant.status.OrderStatus;
import org.lee.rocket.train.common.constant.status.PayStatus;
import org.lee.rocket.train.common.constant.status.UserMoneyLogType;
import org.lee.rocket.train.common.exception.CastException;
import org.lee.rocket.train.common.model.Result;
import org.lee.rocket.train.common.statemachine.StatusTransition;
import org.lee.rocket.train.order.mapper.OrderMapper;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.lee.rocket.train.service.entity.Coupon;
import org.lee.rocket.train.service.entity.GoodsStocksLog;
import org.lee.rocket.train.service.entity.MQEntity;
import org.lee.rocket.train.service.entity.Order;
import org.lee.rocket.train.service.entity.User;
import org.lee.rocket.train.service.entity.UserMoneyLog;
import org.lee.rocket.train.api.ICouponService;
import org.lee.rocket.train.api.IGoodsService;
import org.lee.rocket.train.api.IOrdersService;
import org.lee.rocket.train.service.entity.Goods;
import org.lee.rocket.train.api.IUserService;
import org.springframework.beans.factory.annotation.Value;

import java.time.LocalDateTime;

/**
 * <p>
 * 订单表 服务实现类
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-03
 */
@DubboService(interfaceClass = IOrdersService.class)
@Slf4j
public class OrdersServiceImpl extends ServiceImpl<OrderMapper, Order> implements IOrdersService {

    @DubboReference(version = "1.0.0", group = "default")
    private IGoodsService goodsService;

    @DubboReference(version = "1.0.0", group = "default")
    private IUserService userService;

    @DubboReference(version = "1.0.0", group = "default")
    private ICouponService couponService;

    @Resource
    private DefaultIdentifierGenerator idWorker;

    @Value("${mq.topics.order-failure}")
    private String topic;

    @Value("${mq.tags.order-failure}")
    private String tags;

    @Resource
    private RocketMQTemplate rocketMQTemplate;

    /**
     * 确认订单 (创建订单)
     * @param order 订单信息
     * @return
     */
    @Override
    public Result<?> confirmOrder(Order order) {
        // 校验订单
        checkOrder(order);
        // 生成预订单(用户不可见)
        // Long preOrderId = savePreOrder(order);
        savePreOrder(order);
        try {
            // 扣减库存
            reduceStock(order);
            // 使用优惠券
            reduceCoupun(order);
            // 扣减余额
            reduceMoneyPaid(order);
            // 确认订单
            updateOrderStatus(order);
            // 返回成功状态
            return Result.success();
        } catch (Exception e) {
            // 确认订单失败,发送消息
            // 订单ID 优惠券ID 用户ID 用户余额 商品ID 商品数量
            MQEntity mqEntity = new MQEntity()
                    .setOrderId(order.getOrderId())
                    .setCouponId(order.getCouponId())
                    .setUserId(order.getUserId())
                    .setGoodsId(order.getGoodsId())
                    .setGoodsNumber(order.getGoodsNumber())
                    .setUserMoney(order.getMoneyPaid());
            try {
                failureOrder(topic, tags, order.getOrderId().toString(), JSON.toJSONString(mqEntity));
            } catch (Exception ex) {
                // 订单回退消息发送失败
                log.error("订单确认失败, RocketMQ 回退失败 --- 订单: {} 优惠券: {} 用户: {} 用户余额: {} 商品: {} 商品数量: {}",
                        order.getOrderId(), order.getCouponId(), order.getUserId(), order.getMoneyPaid(),
                        order.getGoodsId(), order.getGoodsNumber());
                log.error("RocketMQ 回退消息发送异常", ex);
                // MQ 消息状态码（写库用）不能当响应码返回，改用 MQ_SEND_MESSAGE_FAIL 响应码
                return Result.fail(ResultCode.MQ_SEND_MESSAGE_FAIL);
            }
            // 订单回退消息发送成功
            log.error("订单确认失败,mq 回退--- 订单: {} 优惠券: {} 用户: {} 用户余额: {} 商品: {} 商品数量: {}",
                    order.getOrderId(), order.getCouponId(), order.getUserId(), order.getMoneyPaid(),
                    order.getGoodsId(), order.getGoodsNumber());
            // 返回失败状态
            return Result.fail(ResultCode.ORDER_CONFIRM_FAIL);
        }
    }

    /**
     * 发送订单失败 mq 消息
     * @param topic 主题
     * @param tag 标签
     * @param keys 业务唯一键
     * @param messageBody 消息体
     */
    private void failureOrder(String topic, String tag, String keys, String messageBody) throws MQBrokerException, RemotingException, InterruptedException, MQClientException {
        Message message = new Message(topic, tag, keys, messageBody.getBytes());
        rocketMQTemplate.getProducer().send(message);

    }

    /**
     * 确认订单
     * @param order 订单信息
     */
    private void updateOrderStatus(Order order) {
        // 变更订单状态  预订单 -> 订单（状态机校验：NO_CONFIRM → CONFIRMED，非法流转直接抛异常）
        OrderStatus from = OrderStatus.of(order.getOrderStatus());
        StatusTransition.check(from, OrderStatus.CONFIRMED);
        order.setOrderStatus(OrderStatus.CONFIRMED.getCode());
        // 变更支付状态
        order.setPayStatus(PayStatus.UNPAID.getCode());
        order.setConfirmTime(LocalDateTime.now());

        // 直接更新订单状态(预订单已存在,无需先查询)
        boolean updateResult = updateById(order);
        if (!updateResult) {
            log.error("订单: {} 确认订单失败,订单不存在或已被修改", order.getOrderId());
            CastException.cast(ResultCode.ORDER_CONFIRM_FAIL);
        }
        
        log.info("订单: {} 确认成功", order.getOrderId());
    }

    /**
     * 扣减余额
     * @param order 订单信息
     */
    private void reduceMoneyPaid(Order order) {
        if (order.getMoneyPaid() != null
                && order.getMoneyPaid().compareTo(0L) > 0) {
            UserMoneyLog userMoneyLog = new UserMoneyLog();
            userMoneyLog.setUserId(order.getUserId());
            userMoneyLog.setMoneyLogType(UserMoneyLogType.PAID.getCode());
            userMoneyLog.setUseMoney(order.getMoneyPaid());
            userMoneyLog.setOrderId(order.getOrderId());

            Result<?> result = userService.updateMoneyPaid(userMoneyLog);
            // 【修复】原用 ShopCode.SUCCESS.getSuccess() 拿布尔字段判断，语义双关；改用标准布尔判断
            if (Boolean.TRUE.equals(result.getSuccess())) {
                log.info("订单: {} 用户: {} 扣减余额成功", order.getOrderId(), order.getUserId());
            } else {
                log.error("订单: {} 用户: {} 扣减余额失败", order.getOrderId(), order.getUserId());
                CastException.cast(ResultCode.USER_MONEY_REDUCE_FAIL);
            }
        }
    }

    /**
     * 扣减优惠券
     * @param order 订单信息
     */
    private void reduceCoupun(Order order) {
        if (order.getCouponId() == null) {
            return;
        }
        Coupon coupon = couponService.getById(order.getCouponId());
        if (coupon != null) {
            coupon.setOrderId(order.getOrderId());
            // 【修复】原用 ShopCode.COUPON_ISUSED.getSuccess() 拿布尔 true 当状态值，语义双关；改用 CouponStatus 状态码
            coupon.setIsUsed(CouponStatus.USED.getCode() == 1);
            coupon.setUsedTime(LocalDateTime.now());

            Result<?> result = couponService.reduceCoupon(coupon);
            if (!result.getSuccess()) {
                log.error("订单: {} 扣减优惠券: {} 失败", order.getOrderId(), coupon.getCouponId());
                CastException.cast(ResultCode.COUPON_USE_FAIL);
            } else {
                log.info("订单: {} 扣减优惠券: {} 成功", order.getOrderId(), coupon.getCouponId());
            }
        }
    }

    /**
     * 扣减库存
     * @param order 订单信息
     */
    private void reduceStock(Order order) {
        // 扣减库存 订单ID 商品ID 商品数量
        GoodsStocksLog goodsStocksLog = new GoodsStocksLog();
        goodsStocksLog.setGoodsId(order.getGoodsId());
        goodsStocksLog.setOrderId(order.getOrderId());
        goodsStocksLog.setGoodsNumber(order.getGoodsNumber());
        Result<?> result = goodsService.reduceStock(goodsStocksLog);
        if (result != null) {
            log.info("订单: {} 扣减库存成功", order.getOrderId());
        }
//        if (result.getCode().equals(String.valueOf(ResultCode.SUCCESS.getCode()))){
//        } else {
//            log.error("订单: " + order.getOrderId() +  " 扣减库存失败");
//            CastException.cast(ResultCode.REDUCE_GOODS_NUM_FAIL);
//        }

    }

    /**
     * 保存预订单
     * @param order 订单信息
     * @return 订单ID
     */
    @SuppressWarnings("null")
    private Long savePreOrder(Order order) {
        // 设置订单状态:0未确认 (用户不可见)
        order.setOrderStatus(OrderStatus.NO_CONFIRM.getCode());

        // 设置订单ID
        Long orderId = idWorker.nextId(null);
        order.setOrderId(orderId);
        // 核算运费(假设机制为: 商品价格 >= 100 不收费, 小于100收费10元)
        Long freight = calculateFreight(order.getGoodsPrice());
        // 【修复】原逻辑异常：运费相等且价格=100 时反而抛异常（即"免邮订单反而被拒"），
        // 且价格≠100 时完全不校验运费。改为标准校验：计算运费 ≠ 订单运费（或未传运费）即抛异常。
        if (order.getShippingFee() == null || !freight.equals(order.getShippingFee())) {
            CastException.cast(ResultCode.ORDER_SHIPPINGFEE_INVALID);
        }
        // 核算订单总金额
        Long orderAmount = order.getGoodsPrice() * order.getGoodsNumber();
        orderAmount = orderAmount + freight;
        if (orderAmount.compareTo(order.getOrderAmount()) != 0) {
            CastException.cast(ResultCode.ORDERAMOUNT_INVALID);
        }
        // 判断用户是否使用余额
        Long moneyPaid = order.getMoneyPaid();
        if (moneyPaid != null) {
            // 判断用户是否小于0
            int i = moneyPaid.compareTo(0L);
            if (i < 0 || i == 0) {
                CastException.cast(ResultCode.MONEY_PAID_LESS_ZERO);
            }
            if (i > 0) {
                User user = userService.findById(order.getUserId());
                if (user.getUserMoney() < moneyPaid) {
                    CastException.cast(ResultCode.MONEY_PAID_INVALID); // 余额不足
                }
            }
        } else {
            // 用户没有用余额, 则将订单余额设置为 0
            order.setMoneyPaid(0L);
        }

        // 判断是否使用优惠券
        Long couponId = order.getCouponId();
        if (couponId != null) {
            Coupon coupon = couponService.getById(couponId);
            // 判断优惠券是否存在
            if (coupon == null) {
                CastException.cast(ResultCode.COUPON_NO_EXIST);
            }
            // 优惠券是否使用
            if (coupon.getIsUsed()) {
                // 【修复】原用 ShopCode.COUPON_ISUSED(状态码) 当响应码抛，改用 COUPON_ALREADY_USED 响应码
                CastException.cast(ResultCode.COUPON_ALREADY_USED);
            }
            order.setCouponPaid(coupon.getCouponPrice());
        } else {
            // 订单没有使用优惠券, 则将优惠券金额设置为 0
            order.setCouponPaid(0L);
        }

        // 核算订单支付金额 = 商品价格 + 运费 - 优惠券金额 - 订单余额
        Long payAmount = order.getOrderAmount() + freight - order.getCouponPaid() - order.getMoneyPaid();
        order.setPayAmount(payAmount);

        // 设置订单下单时间
        order.setAddTime(LocalDateTime.now());

        // 保存预订单数据
        save(order);

        return orderId;
    }

    /**
     * 核算运费
     * @param goodsPrice 商品价格
     * @return 运费
     */
    private Long calculateFreight(Long goodsPrice) {
        if (goodsPrice.compareTo(100L) >= 0) {
            return 0L;
        } else {
            // 返回固定邮费 10元, _00是 long 类型的整数存分
            return 10_00L;
        }
    }

    /**
     * 校验订单信息
     * @param order 订单信息
     */
    @SuppressWarnings("null")
    private void checkOrder(Order order) {
        //1.校验订单是否存在
        if (order == null) {
            //订单不存在
            CastException.cast(ResultCode.ORDER_INVALID);
        }
        //2.校验订单中的商品是否存在
        Goods goods = goodsService.findById(order.getGoodsId());
        if (goods == null) {
            //商品不存在
            CastException.cast(ResultCode.GOODS_NO_EXIST);
        }
        //3.校验下单用户是否存在
        User user = userService.findById(order.getUserId());
        if (user == null) {
            //用户不存在
            CastException.cast(ResultCode.USER_NO_EXIST);
        }
        //4.校验订单金额是否合法
        // 【修复】原逻辑写反：客户端价 == 数据库价时反而抛异常，应改为 != 时抛异常
        if (order.getGoodsPrice() == null
                || goods.getGoodsPrice() == null
                || !order.getGoodsPrice().equals(goods.getGoodsPrice())) {
            CastException.cast(ResultCode.GOODS_PRICE_INVALID);
        }

        //5.校验订单商品数量是否合法
        if (goods.getGoodsNumber() < order.getGoodsNumber()) {
            CastException.cast(ResultCode.GOODS_NUM_NOT_ENOUGH);
        }

        log.info("订单信息校验通过");
    }
}
