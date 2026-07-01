package org.lee.rocket.train.api;

import org.lee.rocket.train.common.model.Result;
import org.lee.rocket.train.service.entity.Coupon;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 优惠券表 服务类
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-03
 */
public interface ICouponService extends IService<Coupon> {

    /**
     * 扣减优惠券
     *
     * @param coupon 优惠券信息
     */
    Result reduceCoupon(Coupon coupon);
}
