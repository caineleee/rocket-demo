package org.lee.rocket.train.common.constant;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

/**
 * @ClassName ShopCode
 * @Description
 * @Author lihongliang
 * @Date 2026/6/5 09:09
 * @Version 1.0
 */
public enum ShopCode {
    //正确
    SUCCESS(true, 1, "正确"),
    //错误
    FAIL(false, 0, "错误"),

    //订单服务消息状态-取消订单
    ORDER_MESSAGE_STATUS_CANCEL(true, 1, "取消订单"),
    //订单服务消息状态-支付成功
    ORDER_MESSAGE_STATUS_ISPAID(true, 2, "支付成功"),
    //付款
    USER_MONEY_PAID(true, 1, "付款"),
    //退款
    USER_MONEY_REFUND(true, 2, "退款"),
    //订单未确认
    ORDER_NO_CONFIRM(false, 0, "订单未确认"),
    //订单已确认
    ORDER_CONFIRM(true, 1, "订单已经确认"),
    //订单已取消
    ORDER_CANCEL(false, 2, "订单已取消"),
    //订单已取消
    ORDER_INVALID(false, 3, "订单无效"),
    //订单已取消
    ORDER_RETURNED(false, 4, "订单已退货"),
    //订单已付款
    ORDER_PAY_STATUS_NO_PAY(true, 0, "订单未付款"),
    //订单已付款
    ORDER_PAY_STATUS_PAYING(true, 1, "订单正在付款"),
    //订单已付款
    ORDER_PAY_STATUS_IS_PAY(true, 2, "订单已付款"),
    //消息正在处理
    MQ_MESSAGE_STATUS_PROCESSING(true, 0, "消息正在处理"),
    //消息处理成功
    MQ_MESSAGE_STATUS_SUCCESS(true, 1, "消息处理成功"),
    //消息处理失败
    MQ_MESSAGE_STATUS_FAIL(false, 2, "消息处理失败"),
    //请求参数有误
    REQUEST_PARAMETER_VALID(false, -1, "请求参数有误"),
    //优惠券已经使用
    COUPON_ISUSED(true, 1, "优惠券已经使用"),
    //优惠券未使用
    COUPON_UNUSED(false, 0, "优惠券未使用"),
    //快递运费不正确
    ORDER_STATUS_UPDATE_FAIL(false, 10001, "订单状态修改失败"),
    //快递运费不正确
    ORDER_SHIPPINGFEE_INVALID(false, 10002, "订单运费不正确"),
    //订单总价格不合法
    ORDERAMOUNT_INVALID(false, 10003, "订单总价格不正确"),
    //订单保存失败
    ORDER_SAVE_ERROR(false, 10004, "订单保存失败"),
    //订单确认失败
    ORDER_CONFIRM_FAIL(false, 10005, "订单确认失败"),
    //商品不存在
    GOODS_NO_EXIST(false, 20001, "商品不存在"),
    //订单价格非法
    GOODS_PRICE_INVALID(false, 20002, "商品价格非法"),
    //商品库存不足
    GOODS_NUM_NOT_ENOUGH(false, 20003, "商品库存不足"),
    //扣减库存失败
    REDUCE_GOODS_NUM_FAIL(false, 20004, "扣减库存失败"),
    //库存记录为空
    REDUCE_GOODS_NUM_EMPTY(false, 20005, "扣减库存失败"),
    //用户账号不能为空
    USER_IS_NULL(false, 30001, "用户账号不能为空"),
    //用户信息不存在
    USER_NO_EXIST(false, 30002, "用户不存在"),
    //余额扣减失败
    USER_MONEY_REDUCE_FAIL(false, 30003, "余额扣减失败"),
    //已经退款
    USER_MONEY_REFUND_ALREADY(true, 30004, "订单已经退过款"),
    //优惠券不不存在
    COUPON_NO_EXIST(false, 40001, "优惠券不存在"),
    //优惠券不合法
    COUPON_INVALIED(false, 40002, "优惠券不合法"),
    //优惠券使用失败
    COUPON_USE_FAIL(false, 40003, "优惠券使用失败"),
    //余额不能小于0
    MONEY_PAID_LESS_ZERO(false, 50001, "余额不能小于0"),
    //余额非法
    MONEY_PAID_INVALID(false, 50002, "余额非法"),
    //Topic不能为空
    MQ_TOPIC_IS_EMPTY(false, 60001, "Topic不能为空"),
    //消息体不能为空
    MQ_MESSAGE_BODY_IS_EMPTY(false, 60002, "消息体不能为空"),
    //消息发送失败
    MQ_SEND_MESSAGE_FAIL(false, 60003, "消息发送失败"),
    //消息并发乐观锁抢夺失败
    MQ_MESSAGE_CONCURRENT_UPDATE_FAIL(false, 60004, "消息并发乐观锁抢夺失败"),
    //支付订单未找到
    //支付订单已支付
    PAYMENT_IS_PAID(false, 70002, "支付订单已支付");

    @Getter
    @Setter
    Boolean success;
    @Setter
    @Getter
    Integer code;
    @Getter
    @Setter
    String message;

    ShopCode() {

    }

    ShopCode(Boolean success, Integer code, String message) {
        this.success = success;
        this.code = code;
        this.message = message;
    }

    @Override
    public String toString() {
        return "ShopCode{" +
                "success=" + success +
                ", code=" + code +
                ", message='" + message + '}';
    }
}
