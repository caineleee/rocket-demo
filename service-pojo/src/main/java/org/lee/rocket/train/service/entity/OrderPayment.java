package org.lee.rocket.train.service.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * <p>
 * 订单支付表
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-03
 */
@Getter
@Setter
@ToString
@TableName("tb_order_payment")
public class OrderPayment implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 支付编号
     */
    @TableId("pay_id")
    private Long payId;

    /**
     * 订单编号
     */
    @TableField("order_id")
    private Long orderId;

    /**
     * 支付金额
     */
    @TableField("pay_amount")
    private BigDecimal payAmount;

    /**
     * 是否已支付：0否 1是
     */
    @TableField("is_paid")
    private Boolean isPaid;
}
