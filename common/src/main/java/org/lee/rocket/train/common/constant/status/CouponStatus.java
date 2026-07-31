package org.lee.rocket.train.common.constant.status;

import lombok.Getter;

/**
 * 优惠券状态（写入 tb_coupon.is_used 列）
 * <p>
 * 状态值：
 * <pre>
 *   UNUSED(0) 未使用
 *   USED(1)   已使用
 * </pre>
 * <p>
 * 设计说明：
 * - 实体 is_used 字段是 Boolean，这里 code 用 int 表达语义，业务代码写入时用
 *   {@code coupon.setIsUsed(CouponStatus.USED.getCode() == 1)} 转换
 * - 修复点：原 OrdersServiceImpl 用 {@code ShopCode.COUPON_ISUSED.getSuccess()}
 *   拿布尔字段当状态值写库，语义双关；现在用 code 表达，清晰
 *
 * @author lihongliang
 */
@Getter
public enum CouponStatus {

    UNUSED(0, "未使用"),
    USED(1, "已使用");

    private final int code;
    private final String desc;

    CouponStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public boolean canTransitionTo(CouponStatus target) {
        return this == UNUSED && target == USED;
    }

    public static CouponStatus of(int code) {
        for (CouponStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知优惠券状态码: " + code);
    }
}
