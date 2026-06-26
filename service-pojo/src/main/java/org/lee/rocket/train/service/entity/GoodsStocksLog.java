package org.lee.rocket.train.service.entity;

import com.baomidou.mybatisplus.annotation.IdType;
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
 * 订单商品日志表
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-22
 */
@Getter
@Setter
@ToString
@TableName("tb_goods_stocks_log")
public class GoodsStocksLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID (自增)
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 商品ID
     */
    @TableField("goods_id")
    private Long goodsId;

    /**
     * 订单ID
     */
    @TableField("order_id")
    private Long orderId;

    /**
     * 库存数量
     */
    @TableField("goods_number")
    private Integer goodsNumber;

    /**
     * 记录时间
     */
    @TableField("log_time")
    private LocalDateTime logTime;
}
