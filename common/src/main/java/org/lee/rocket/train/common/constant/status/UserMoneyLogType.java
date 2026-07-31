package org.lee.rocket.train.common.constant.status;

import lombok.Getter;

/**
 * 用户资金流水类型（写入 tb_user_money_log.money_log_type 列）
 * <p>
 * 类型值：
 * <pre>
 *   PAID(1)   付款 —— 下单扣减余额
 *   REFUND(2) 退款 —— 订单回退返还余额
 * </pre>
 * <p>
 * 设计说明：
 * - 这是业务类型标识（流水分类），不是状态机，无需 canTransitionTo
 * - 与「已退款」响应码 USER_MONEY_REFUND_ALREADY(30004) 区分：
 *   本枚举是写库的类型标识，30004 是抛异常用的响应码（归 ResultCode）
 *
 * @author lihongliang
 */
@Getter
public enum UserMoneyLogType {

    PAID(1, "付款"),
    REFUND(2, "退款");

    private final int code;
    private final String desc;

    UserMoneyLogType(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static UserMoneyLogType of(int code) {
        for (UserMoneyLogType t : values()) {
            if (t.code == code) {
                return t;
            }
        }
        throw new IllegalArgumentException("未知资金流水类型码: " + code);
    }
}
