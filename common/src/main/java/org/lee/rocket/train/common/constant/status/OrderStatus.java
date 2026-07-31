package org.lee.rocket.train.common.constant.status;

import lombok.Getter;

/**
 * 订单状态机（写入 tb_order.order_status 列）
 * <p>
 * 状态流转链路：
 * <pre>
 *   NO_CONFIRM(0) ──确认──→ CONFIRMED(1)
 *   NO_CONFIRM(0) ──校验失败──→ INVALID(3)
 *   CONFIRMED(1)  ──取消──→ CANCELLED(2)
 *   CONFIRMED(1)  ──退货──→ RETURNED(4)
 *   CANCELLED / INVALID / RETURNED 为终态，不可再流转
 * </pre>
 * <p>
 * 设计说明（参考大厂订单中心做法）：
 * - 状态码与响应码物理隔离，避免误用响应码写库（曾因 PAYMENT_IS_PAID(70002) 写进 tinyint 导致 Data truncation）
 * - 枚举不可变（无 Setter），状态值稳定
 * - 内置 canTransitionTo 描述合法流转，配合 StatusTransition.check 校验非法流转
 *
 * @author lihongliang
 */
@Getter
public enum OrderStatus {

    NO_CONFIRM(0, "待确认"),
    CONFIRMED(1, "已确认"),
    CANCELLED(2, "已取消"),
    INVALID(3, "无效"),
    RETURNED(4, "已退货");

    /** 写入数据库的状态值 */
    private final int code;
    /** 状态描述，用于日志和展示 */
    private final String desc;

    OrderStatus(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 当前状态是否可以流转到目标状态
     * 集中维护流转规则，业务代码调用前先校验，避免脏数据
     *
     * @param target 目标状态
     * @return true 表示合法流转
     */
    public boolean canTransitionTo(OrderStatus target) {
        return switch (this) {
            // 待确认：可确认（正常）或失效（校验不通过）
            case NO_CONFIRM -> target == CONFIRMED || target == INVALID;
            // 已确认：可取消或退货
            case CONFIRMED -> target == CANCELLED || target == RETURNED;
            // 终态：不可再流转
            case CANCELLED, INVALID, RETURNED -> false;
        };
    }

    /**
     * 根据 code 反查枚举，用于从数据库读出状态后转成枚举做流转校验
     *
     * @param code 数据库里的状态值
     * @return 对应的 OrderStatus
     */
    public static OrderStatus of(int code) {
        for (OrderStatus s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知订单状态码: " + code);
    }
}
