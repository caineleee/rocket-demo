package org.lee.rocket.train.order.service.impl;


import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import jakarta.annotation.Resource;
import lombok.extern.log4j.Log4j2;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.lee.rocket.train.common.constant.ShopCode;
import org.lee.rocket.train.common.exception.CastException;
import org.lee.rocket.train.common.model.Result;
import org.lee.rocket.train.order.mapper.OrdersMapper;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.lee.rocket.train.service.entity.Coupon;
import org.lee.rocket.train.service.entity.GoodsStocksLog;
import org.lee.rocket.train.service.entity.User;
import org.lee.rocket.train.service.entity.UserMoneyLog;
import org.lee.rocket.train.serviceapi.ICouponService;
import org.lee.rocket.train.serviceapi.IGoodsService;
import org.lee.rocket.train.serviceapi.IOrdersService;
import org.lee.rocket.train.service.entity.Goods;
import org.lee.rocket.train.service.entity.Orders;
import org.lee.rocket.train.serviceapi.IUserService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 订单表 服务实现类
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-03
 */
@Log4j2
@Service
@DubboService(interfaceClass = IOrdersService.class)
public class OrdersServiceImpl extends ServiceImpl<OrdersMapper, Orders> implements IOrdersService {

    @DubboReference(version = "1.0.0", group = "default")
    private IGoodsService goodsService;

    @DubboReference(version = "1.0.0", group = "default")
    private IUserService userService;

    @DubboReference(version = "1.0.0", group = "default")
    private ICouponService couponService;

    @Resource
    private DefaultIdentifierGenerator idWorker;

    /**
     * 确认订单 (创建订单)
     * @param orders 订单信息
     * @return
     */
    @Override
    public Result confirmOrder(Orders orders) {
        // 校验订单
        checkOrder(orders);
        // 生成预订单(用户不可见)
        Long preOrderId = savePreOrder(orders);
        try {
            // 扣减库存
            reduceStock(orders);
            // 使用优惠券
            reduceCoupun(orders);
            // 扣减余额
            reduceMoneyPaid(orders);
            // 确认订单
            updateOrderStatus(orders);
            // 返回成功状态
            return Result.success(ShopCode.SUCCESS);
        } catch (Exception e) {
            // 确认订单失败,发送消息

            // 返回失败状态
        }

        return null;
    }

    /**
     * 确认订单
     * @param orders 订单信息
     */
    private void updateOrderStatus(Orders orders) {
        // 变更订单状态  预订单 -> 订单
        orders.setOrderStatus(ShopCode.ORDER_CONFIRM.getCode());
        // 变更支付状态
        orders.setPayStatus(ShopCode.ORDER_PAY_STATUS_NO_PAY.getCode());
        orders.setConfirmTime(LocalDateTime.now());

        // 直接更新订单状态(预订单已存在,无需先查询)
        boolean updateResult = updateById(orders);
        if (!updateResult) {
            log.error("订单: {} 确认订单失败,订单不存在或已被修改", orders.getOrderId());
            CastException.cast(ShopCode.ORDER_CONFIRM_FAIL);
        }
        
        log.info("订单: {} 确认成功", orders.getOrderId());
    }

    /**
     * 扣减余额
     * @param orders 订单信息
     */
    private void reduceMoneyPaid(Orders orders) {
        if (orders.getMoneyPaid() != null
                && orders.getMoneyPaid().compareTo(BigDecimal.ZERO) > 0) {
            UserMoneyLog userMoneyLog = new UserMoneyLog();
            userMoneyLog.setUserId(orders.getUserId());
            userMoneyLog.setMoneyLogType(ShopCode.USER_MONEY_PAID.getCode());
            userMoneyLog.setUseMoney(orders.getMoneyPaid());
            userMoneyLog.setOrderId(orders.getOrderId());

            Result<?> result = userService.updateMoneyPaid(userMoneyLog);
            if (result.getSuccess().equals(ShopCode.SUCCESS.getSuccess())) {
                log.info("订单: " + orders.getOrderId() + " 用户: " + orders.getUserId() + " 扣减余额成功");
            } else {
                log.error("订单: " + orders.getOrderId() + " 用户: " + orders.getUserId() + " 扣减余额失败");
                CastException.cast(ShopCode.USER_MONEY_REDUCE_FAIL);
            }
        }
    }

    /**
     * 扣减优惠券
     * @param orders 订单信息
     */
    private void reduceCoupun(Orders orders) {
        if (orders.getCouponId() == null) {
            return;
        }
        Coupon coupon = couponService.getById(orders.getCouponId());
        if (coupon != null) {
            coupon.setOrderId(orders.getOrderId());
            coupon.setIsUsed(ShopCode.COUPON_ISUSED.getSuccess());
            coupon.setUsedTime(LocalDateTime.now());

            Result result = couponService.reduceCoupon(coupon);
            if (!result.getSuccess()) {
                log.error("订单: " + orders.getOrderId() +  " 扣减优惠券: " + coupon.getCouponId() +" 失败");
                CastException.cast(ShopCode.COUPON_USE_FAIL);
            } else {
                log.info("订单: " + orders.getOrderId() +  " 扣减优惠券: " + coupon.getCouponId() +" 成功");
            }
        }
    }

    /**
     * 扣减库存
     * @param orders 订单信息
     */
    private void reduceStock(Orders orders) {
        // 扣减库存 订单ID 商品ID 商品数量
        GoodsStocksLog goodsStocksLog = new GoodsStocksLog();
        goodsStocksLog.setGoodsId(orders.getGoodsId());
        goodsStocksLog.setOrderId(orders.getOrderId());
        goodsStocksLog.setGoodsNumber(orders.getGoodsNumber());
        Result result = goodsService.reduceStock(goodsStocksLog);
        if (!result.getSuccess().equals(ShopCode.SUCCESS.getSuccess())){
            log.info("订单: " + orders.getOrderId() +  " 扣减库存成功");
        } else {
            log.error("订单: " + orders.getOrderId() +  " 扣减库存失败");
            CastException.cast(ShopCode.REDUCE_GOODS_NUM_FAIL);
        }

    }

    /**
     * 保存预订单
     * @param orders 订单信息
     * @return 订单ID
     */
    private Long savePreOrder(Orders orders) {
        // 设置订单状态:0未确认 (用户不可见)
        orders.setOrderStatus(ShopCode.ORDER_NO_CONFIRM.getCode());

        // 设置订单ID
        Long orderId = idWorker.nextId(null);
        orders.setOrderId(orderId);
        // 核算运费(假设机制为: 商品价格 >= 100 不收费, 小于100收费10元)
        BigDecimal freight = calculateFreight(orders.getGoodsPrice());
        if (freight.compareTo(orders.getShippingFee()) == 0) {
            CastException.cast(ShopCode.ORDER_SHIPPINGFEE_INVALID);
        }
        // 核算订单总金额
        BigDecimal orderAmount = orders.getGoodsPrice().multiply(BigDecimal.valueOf(orders.getGoodsNumber()));
        orderAmount.add(freight);
        if (orderAmount.compareTo(orders.getOrderAmount()) != 0) {
            CastException.cast(ShopCode.ORDERAMOUNT_INVALID);
        }
        // 判断用户是否使用余额
        BigDecimal moneyPaid = orders.getMoneyPaid();
        if (moneyPaid != null) {
            // 判断用户是否小于0
            int i = moneyPaid.compareTo(BigDecimal.ZERO);
            if (i < 0 || i == 0) {
                CastException.cast(ShopCode.MONEY_PAID_LESS_ZERO);
            }
            if (i > 0) {
                User user = userService.findById(orders.getUserId());
                if (user.getUserMoney() < moneyPaid.longValue()) {
                    CastException.cast(ShopCode.MONEY_PAID_INVALID); // 余额不足
                }
            }
        } else {
            // 用户没有用余额, 则将订单余额设置为 0
            orders.setMoneyPaid(BigDecimal.ZERO);
        }

        // 判断是否使用优惠券
        Long couponId = orders.getCouponId();
        if (couponId != null) {
            Coupon coupon = couponService.getById(couponId);
            // 判断优惠券是否存在
            if (coupon == null) {
                CastException.cast(ShopCode.COUPON_NO_EXIST);
            }
            // 优惠券是否使用
            if (coupon.getIsUsed()) {
                CastException.cast(ShopCode.COUPON_ISUSED);
            }
            orders.setCouponPaid(coupon.getCouponPrice());
        } else {
            // 订单没有使用优惠券, 则将优惠券金额设置为 0
            orders.setCouponPaid(BigDecimal.ZERO);
        }

        // 核算订单支付金额 = 商品价格 + 运费 - 优惠券金额 - 订单余额
        BigDecimal payAmount = orders.getOrderAmount().add(freight).subtract(orders.getCouponPaid()).subtract(orders.getMoneyPaid());
        orders.setPayAmount(payAmount);

        // 设置订单下单时间
        orders.setAddTime(LocalDateTime.now());

        // 保存预订单数据
        save(orders);

        return orderId;
    }

    /**
     * 核算运费
     * @param goodsPrice 商品价格
     * @return 运费
     */
    private BigDecimal calculateFreight(BigDecimal goodsPrice) {
        if (goodsPrice.compareTo(new BigDecimal(100)) >= 0) {
            return BigDecimal.ZERO;
        } else {
            return new BigDecimal(10);
        }
    }

    /**
     * 校验订单信息
     * @param orders 订单信息
     */
    private void checkOrder(Orders orders) {
        //1.校验订单是否存在
        if (orders == null) {
            //订单不存在
            CastException.cast(ShopCode.ORDER_INVALID);
        }
        //2.校验订单中的商品是否存在
        Goods goods = goodsService.findById(orders.getGoodsId());
        if (goods == null) {
            //商品不存在
            CastException.cast(ShopCode.GOODS_NO_EXIST);
        }
        //3.校验下单用户是否存在
        User user = userService.findById(orders.getUserId());
        if (user == null) {
            //用户不存在
            CastException.cast(ShopCode.USER_NO_EXIST);
        }
        //4.校验订单金额是否合法
        if (orders.getGoodsPrice() == null
                || goods.getGoodsPrice() == null
                || orders.getGoodsPrice().compareTo(goods.getGoodsPrice()) != 0) {
            CastException.cast(ShopCode.GOODS_PRICE_INVALID);
        }

        //5.校验订单商品数量是否合法
        if (goods.getGoodsNumber() < orders.getGoodsNumber()) {
            CastException.cast(ShopCode.GOODS_NUM_NOT_ENOUGH);
        }

        log.info("订单信息校验通过");
    }
}
