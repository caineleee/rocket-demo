package org.lee.rocket.train.coupon.service.impl;

import org.apache.dubbo.config.annotation.DubboService;
import org.lee.rocket.train.common.constant.ShopCode;
import org.lee.rocket.train.common.exception.CastException;
import org.lee.rocket.train.common.model.Result;
import org.lee.rocket.train.coupon.mapper.CouponMapper;
import org.lee.rocket.train.api.ICouponService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.lee.rocket.train.service.entity.Coupon;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 优惠券表 服务实现类
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-03
 */
@Service
@DubboService(interfaceClass = ICouponService.class)
public class CouponServiceImpl extends ServiceImpl<CouponMapper, Coupon> implements ICouponService {
    /**
     * 扣减优惠券
     * @param coupon 优惠券信息
     * @return 扣减结果
     */
    @Override
    public Result reduceCoupon(Coupon coupon) {
        if (coupon == null || coupon.getCouponId() == null) {
            CastException.cast(ShopCode.REQUEST_PARAMETER_VALID);
        }
        boolean updateResult = updateById(coupon);
        if (!updateResult) {
            CastException.cast(ShopCode.COUPON_USE_FAIL);
        }
        return Result.success(ShopCode.SUCCESS);
    }
}
