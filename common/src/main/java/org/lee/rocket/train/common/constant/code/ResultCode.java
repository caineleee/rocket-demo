package org.lee.rocket.train.common.constant.code;

import lombok.Getter;

/**
 * 统一响应码（返回前端 / 抛业务异常用，<b>不写库</b>）
 * <p>
 * 与状态码（{@link org.lee.rocket.train.common.constant.status} 包下各枚举）物理隔离，
 * 避免误用响应码写库（曾因 PAYMENT_IS_PAID(70002) 写进 tinyint 导致 Data truncation）。
 * <p>
 * 分段规则（参考阿里 Java 开发手册，按服务/模块分段）：
 * <pre>
 *   通用   2xx/4xx/5xx
 *   订单   1xxxx
 *   商品   2xxxx
 *   用户   3xxxx
 *   优惠券 4xxxx
 *   资金   5xxxx
 *   MQ    6xxxx
 *   支付   7xxxx
 *   Redis 8xxxx
 * </pre>
 * <p>
 * 设计说明：
 * - 去掉原 ShopCode 的 success 布尔字段（语义双关被误用），是否成功由 {@code code == 200} 判断
 * - 枚举不可变（无 Setter）
 * - 新增 COUPON_ALREADY_USED / PAYMENT_NOT_PAID：纠正原「把状态码当响应码抛」的误用
 *
 * @author lihongliang
 */
@Getter
public enum ResultCode {

    // ==================== 通用 ====================
    SUCCESS(200, "操作成功"),
    FAIL(500, "操作失败"),
    REQUEST_PARAMETER_VALID(400, "请求参数有误"),

    // ==================== 订单 1xxxx ====================
    ORDER_INVALID(10006, "订单无效"),
    ORDER_STATUS_UPDATE_FAIL(10001, "订单状态修改失败"),
    ORDER_SHIPPINGFEE_INVALID(10002, "订单运费不正确"),
    ORDERAMOUNT_INVALID(10003, "订单总价格不正确"),
    ORDER_SAVE_ERROR(10004, "订单保存失败"),
    ORDER_CONFIRM_FAIL(10005, "订单确认失败"),

    // ==================== 商品 2xxxx ====================
    GOODS_NO_EXIST(20001, "商品不存在"),
    GOODS_PRICE_INVALID(20002, "商品价格非法"),
    GOODS_NUM_NOT_ENOUGH(20003, "商品库存不足"),
    REDUCE_GOODS_NUM_FAIL(20004, "扣减库存失败"),
    REDUCE_GOODS_NUM_EMPTY(20005, "库存记录为空"),

    // ==================== 用户 3xxxx ====================
    USER_IS_NULL(30001, "用户账号不能为空"),
    USER_NO_EXIST(30002, "用户不存在"),
    USER_MONEY_REDUCE_FAIL(30003, "余额扣减失败"),
    USER_MONEY_REFUND_ALREADY(30004, "订单已经退过款"),
    USER_LOGIN_FAIL(30005, "用户名或密码错误"),
    TOKEN_INVALID(30006, "Token无效"),
    TOKEN_EXPIRED(30007, "Token已过期"),
    TOKEN_NOT_FOUND(30008, "未携带Token"),
    TOKEN_INVALIDATED(30009, "Token已失效，请重新登录"),
    PERMISSION_DENIED(30010, "权限不足"),
    REFRESH_TOKEN_INVALID(30011, "Refresh Token无效"),

    // ==================== 优惠券 4xxxx ====================
    COUPON_NO_EXIST(40001, "优惠券不存在"),
    COUPON_INVALID(40002, "优惠券不合法"),
    COUPON_USE_FAIL(40003, "优惠券使用失败"),
    // 新增：纠正原 OrdersServiceImpl 把 COUPON_ISUSED(状态码) 当响应码抛的误用
    COUPON_ALREADY_USED(40004, "优惠券已使用"),

    // ==================== 资金 5xxxx ====================
    MONEY_PAID_LESS_ZERO(50001, "余额不能小于0"),
    MONEY_PAID_INVALID(50002, "余额非法"),

    // ==================== MQ 6xxxx ====================
    MQ_TAG_IS_EMPTY(60000, "Tag不能为空"),
    MQ_TOPIC_IS_EMPTY(60001, "Topic不能为空"),
    MQ_MESSAGE_BODY_IS_EMPTY(60002, "MQ消息体不能为空"),
    MQ_SEND_MESSAGE_FAIL(60003, "消息发送失败"),
    MQ_MESSAGE_CONCURRENT_UPDATE_FAIL(60004, "消息并发乐观锁抢夺失败"),

    // ==================== 支付 7xxxx ====================
    PAYMENT_NOT_FOUND(70001, "支付订单未找到"),
    PAYMENT_IS_PAID(70002, "支付订单已支付"),
    PAYMENT_FAILURE(70003, "支付订单失败"),
    // 新增：纠正原 PaymentServiceImpl 把 ORDER_PAY_STATUS_NO_PAY(状态码) 当响应码抛的误用
    PAYMENT_NOT_PAID(70004, "支付订单未支付"),

    // ==================== Redis 8xxxx ====================
    REDIS_LOCK_ACQUIRE_FAIL(80001, "分布式锁获取失败，请稍后重试"),
    REDIS_INCR_EXCEED_MAX(80002, "操作频率超限，请稍后重试"),
    REDIS_INCR_BELOW_MIN(80003, "库存不足，无法扣减"),
    REDIS_GET_MISS_FAST_FAIL(80004, "缓存未命中，请求被拒绝"),
    REDIS_SPEL_SECURITY_VIOLATION(80005, "SpEL 表达式包含不安全调用"),
    REDIS_OPERATION_ERROR(80006, "Redis 操作异常");

    /** 响应码 */
    private final int code;
    /** 提示信息 */
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
