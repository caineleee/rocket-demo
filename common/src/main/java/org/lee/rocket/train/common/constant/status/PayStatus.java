package org.lee.rocket.train.common.constant.status;

import lombok.Getter;

/**
 * 支付状态机（写入 tb_order.pay_status 和 tb_payment.is_paid 列）
 * <p>
 * 状态流转链路：
 * <pre>
 *   UNPAID(0)  ──发起支付──→ PAYING(1)
 *   PAYING(1)  ──支付成功──→ PAID(2)
 *   PAID(2)    ──退款────→ REFUNDED(3)
 * </pre>
 * <p>
 * 设计说明：
 * - Order.payStatus 与 Payment.isPaid 共用此枚举，二者是同一支付状态机的不同视角
 * - 新增 REFUNDED(3) 终态，明确「已退款」语义（原 ShopCode 没有支付维度的退款状态）
 * - 修复点：原 PaymentServiceImpl.callbackPayment 错用 PAYMENT_IS_PAID(70002 响应码) 写库，
 *   现在类型安全，编译期就能防误用
 *
 * @author lihongliang
 */
@Getter
public enum PayStatus {

    UNPAID(0, "未支付"),
    PAYING(1, "支付中"),
    PAID(2, "已支付"),
    REFUNDED(3, "已退款");

    private final int code;
    private final String desc;

    PayStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public boolean canTransitionTo(PayStatus target) {
        return switch (this) {
            // 未支付：可发起支付进入支付中
            case UNPAID -> target == PAYING;
            // 支付中：可支付成功
            case PAYING -> target == PAID;
            // 已支付：可退款
            case PAID -> target == REFUNDED;
            // 已退款：终态
            case REFUNDED -> false;
        };
    }

    public static PayStatus of(int code) {
        for (PayStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知支付状态码: " + code);
    }
}
