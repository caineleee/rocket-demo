package org.lee.rocket.train.order.service.impl;


import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import org.apache.seata.spring.annotation.GlobalTransactional;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
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
import org.lee.rocket.train.service.entity.Order;
import org.lee.rocket.train.service.entity.User;
import org.lee.rocket.train.service.entity.UserMoneyLog;
import org.lee.rocket.train.api.ICouponService;
import org.lee.rocket.train.api.IGoodsService;
import org.lee.rocket.train.api.IOrdersService;
import org.lee.rocket.train.service.entity.Goods;
import org.lee.rocket.train.api.IUserService;

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

    /**
     * 确认订单（创建订单）—— Seata 全局事务入口（TM）
     * <p>
     * 【事务边界】confirmOrder 是 TM，通过 Dubbo 同步调用 goods/coupon/user 三个 RM：
     * <ul>
     *   <li>reduceStock → goods-service（RM，扣减库存）</li>
     *   <li>reduceCoupun → coupon-service（RM，扣减优惠券）</li>
     *   <li>reduceMoneyPaid → user-service（RM，扣减余额）</li>
     *   <li>updateOrderStatus → order 自身本地事务（RM，订单状态流转）</li>
     * </ul>
     * 任意 RM 抛异常 → Seata 全局回滚（undo_log 反向 SQL 自动恢复），无需 MQ 失败补偿。
     * 异常冒泡到 GlobalExceptionHandler 统一转 Result 返回前端。
     * <p>
     * 【原 MQ 补偿已移除】原 try-catch 捕获异常后发 order-failure MQ 消息触发异步回退，
     * 现由 Seata AT 模式同步回滚替代（更可靠、无消息丢失风险）。
     *
     * @param order 订单信息
     * @return 结果
     */
    @GlobalTransactional(rollbackFor = Exception.class, timeoutMills = 60000)
    @Override
    public Result<?> confirmOrder(Order order) {
        // 校验订单（商品/用户/价格/库存）
        checkOrder(order);
        // 生成预订单（用户不可见，本地事务）
        savePreOrder(order);
        // 扣减库存（RM: goods-service，Dubbo 同步调用，XID 自动传播）
        reduceStock(order);
        // 使用优惠券（RM: coupon-service）
        reduceCoupun(order);
        // 扣减余额（RM: user-service）
        reduceMoneyPaid(order);
        // 确认订单（本地事务：NO_CONFIRM → CONFIRMED）
        updateOrderStatus(order);
        // 全局事务提交（TM 通知 TC，TC 通知各 RM 提交分支）
        return Result.success();
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
