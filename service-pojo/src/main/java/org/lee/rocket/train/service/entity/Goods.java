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
 * 商品表
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-03
 */
@Getter
@Setter
@ToString
@TableName("tb_goods")
public class Goods implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @TableId("goods_id")
    private Long goodsId;

    /**
     * 商品名称
     */
    @TableField("goods_name")
    private String goodsName;

    /**
     * 商品库存
     */
    @TableField("goods_number")
    private Integer goodsNumber;

    /**
     * 商品价格
     */
    @TableField("goods_price")
    private Long goodsPrice;

    /**
     * 商品描述
     */
    @TableField("goods_desc")
    private String goodsDesc;

    /**
     * 添加时间
     */
    @TableField("add_time")
    private LocalDateTime addTime;
}
