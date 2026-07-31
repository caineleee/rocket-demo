package org.lee.rocket.train.coupon.service.impl;

import org.apache.dubbo.config.annotation.DubboService;
import org.lee.rocket.train.common.constant.code.ResultCode;
import org.lee.rocket.train.common.exception.CastException;
import org.lee.rocket.train.common.model.Result;
import org.lee.rocket.train.coupon.mapper.CouponMapper;
import org.lee.rocket.train.api.ICouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.lee.rocket.train.service.entity.Coupon;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * <p>
 * 优惠券表 服务实现类
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-03
 */
@DubboService(interfaceClass = ICouponService.class)
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {

    /**
     * 扣减优惠券
     *
     * 【修复内容】
     * 1. 用条件更新（WHERE is_used = false）替代盲目 updateById，
     *    靠 DB 行锁保证原子性，避免并发场景下同一张券被多次扣减
     * 2. 只更新 is_used / order_id / used_time 三个字段，不再透传入参的所有字段
     *    （原 updateById 会更新所有非 null 字段，存在 mass assignment 风险——
     *    调用方传入 couponPrice=0 就能篡改优惠券面值）
     * 3. 根据 affectedRows 判断是否扣减成功（0 行 = 券不存在或已使用）
     * 4. 加 @Transactional 保证原子性
     *
     * @param coupon 优惠券信息（只需 couponId 和 orderId）
     * @return 扣减结果
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Result<?> reduceCoupon(Coupon coupon) {
        if (coupon == null || coupon.getCouponId() == null) {
            CastException.cast(ResultCode.REQUEST_PARAMETER_VALID);
        }
        // 条件更新：只有 is_used = false 的优惠券才能被扣减
        // 靠 DB 行锁保证原子性，避免并发场景下同一张券被多次扣减
        @SuppressWarnings("null")
        boolean success = lambdaUpdate()
                .eq(Coupon::getCouponId, coupon.getCouponId())
                .eq(Coupon::getIsUsed, false)
                .set(Coupon::getIsUsed, true)
                .set(Coupon::getOrderId, coupon.getOrderId())
                .set(Coupon::getUsedTime, LocalDateTime.now())
                .update();
        if (!success) {
            // 更新 0 行：优惠券不存在或已被使用
            CastException.cast(ResultCode.COUPON_USE_FAIL);
        }
        return Result.success();
    }
}
