package org.lee.rocket.train.service.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 订单表
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-03
 */
@Getter
@Setter
@ToString
@TableName("tb_orders")
public class Order implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 订单ID
     */
    @TableId("order_id")
    private Long orderId;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 订单状态：0未确认 1已确认 2已取消 3无效 4退款
     */
    @TableField("order_status")
    private Integer orderStatus;

    /**
     * 支付状态：0未支付 1支付中 2已支付
     */
    @TableField("pay_status")
    private Integer payStatus;

    /**
     * 发货状态：0未发货 1已发货 2已收货 3已退货
     */
    @TableField("shipping_status")
    private Boolean shippingStatus;

    /**
     * 收货地址
     */
    @TableField("address")
    private String address;

    /**
     * 收货人
     */
    @TableField("consignee")
    private String consignee;

    /**
     * 商品ID
     */
    @TableField("goods_id")
    private Long goodsId;

    /**
     * 商品数量
     */
    @TableField("goods_number")
    private Integer goodsNumber;

    /**
     * 商品价格
     */
    @TableField("goods_price")
    private Long goodsPrice;

    /**
     * 商品总价
     */
    @TableField("goods_amount")
    private Long goodsAmount;

    /**
     * 运费
     */
    @TableField("shipping_fee")
    private Long shippingFee;

    /**
     * 订单价格
     */
    @TableField("order_amount")
    private Long orderAmount;

    /**
     * 优惠券ID
     */
    @TableField("coupon_id")
    private Long couponId;

    /**
     * 优惠券抵扣金额
     */
    @TableField("coupon_paid")
    private Long couponPaid;

    /**
     * 已付金额
     */
    @TableField("money_paid")
    private Long moneyPaid;

    /**
     * 支付金额
     */
    @TableField("pay_amount")
    private Long payAmount;

    /**
     * 创建时间
     */
    @TableField("add_time")
    private LocalDateTime addTime;

    /**
     * 订单确认时间
     */
    @TableField("confirm_time")
    private LocalDateTime confirmTime;

    /**
     * 支付时间
     */
    @TableField("pay_time")
    private LocalDateTime payTime;
}
