package org.lee.rocket.train.service.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * <p>
 * 优惠券表
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-03
 */
@Getter
@Setter
@ToString
@TableName("tb_coupon")
public class Coupon implements Serializable {


    private static final long serialVersionUID = 1L;

    /**
     * 优惠券ID
     */
    @TableId("coupon_id")
    private Long couponId;

    /**
     * 优惠券金额
     */
    @TableField("coupon_price")
    private BigDecimal couponPrice;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 订单ID
     */
    @TableField("order_id")
    private Long orderId;

    /**
     * 是否使用 0未使用 1已使用
     */
    @TableField("is_used")
    private Boolean isUsed;

    /**
     * 使用时间
     */
    @TableField("used_time")
    private LocalDateTime usedTime;
}
