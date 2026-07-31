package org.lee.rocket.train.common.statemachine;

import org.lee.rocket.train.common.constant.status.OrderStatus;
import org.lee.rocket.train.common.constant.status.PayStatus;

/**
 * 状态机流转校验工具
 * <p>
 * 用法：在业务代码改变状态前调用，非法流转直接抛 IllegalStateException，避免脏数据：
 * <pre>
 *   OrderStatus from = OrderStatus.of(order.getOrderStatus());
 *   StatusTransition.check(from, OrderStatus.CONFIRMED);
 *   order.setOrderStatus(OrderStatus.CONFIRMED.getCode());
 * </pre>
 * <p>
 * 设计说明：
 * - 集中校验，把「哪些状态可以转到哪些状态」的规则收敛到枚举的 canTransitionTo 方法
 * - 不引入状态机框架（Spring StateMachine / COLA），本项目规模用枚举流转表足够
 * - 抛 IllegalStateException 而非业务异常，因为非法流转属于编程错误（不该发生的流转），
 *   应让开发者立刻发现，而不是返回给前端
 *
 * @author lihongliang
 */
public final class StatusTransition {

    private StatusTransition() {
    }

    /**
     * 校验订单状态流转合法性
     *
     * @param from 当前状态
     * @param to   目标状态
     * @return 目标状态（通过校验）
     * @throws IllegalStateException 非法流转时抛出
     */
    public static OrderStatus check(OrderStatus from, OrderStatus to) {
        if (!from.canTransitionTo(to)) {
            throw new IllegalStateException(
                    "非法订单状态流转: " + from.getDesc() + "(" + from.getCode() + ")"
                            + " -> " + to.getDesc() + "(" + to.getCode() + ")");
        }
        return to;
    }

    /**
     * 校验支付状态流转合法性
     *
     * @param from 当前状态
     * @param to   目标状态
     * @return 目标状态（通过校验）
     * @throws IllegalStateException 非法流转时抛出
     */
    public static PayStatus check(PayStatus from, PayStatus to) {
        if (!from.canTransitionTo(to)) {
            throw new IllegalStateException(
                    "非法支付状态流转: " + from.getDesc() + "(" + from.getCode() + ")"
                            + " -> " + to.getDesc() + "(" + to.getCode() + ")");
        }
        return to;
    }
}
