package org.lee.rocket.train.service.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * <p>
 * 用户余额变动日志表
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-05
 */
@Getter
@Setter
@ToString
@TableName("tb_user_money_log")
public class UserMoneyLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 订单ID（关联订单）
     */
    @TableField("order_id")
    private Long orderId;

    /**
     * 日志类型：1=订单付款，2=订单退款
     */
    @TableField("money_log_type")
    private Integer moneyLogType;

    /**
     * 操作金额（单位：元）
     */
    @TableField("use_money")
    private Long useMoney;

    /**
     * 日志时间（精确到毫秒）
     */
    @TableField("create_time")
    private LocalDateTime createTime;
}
