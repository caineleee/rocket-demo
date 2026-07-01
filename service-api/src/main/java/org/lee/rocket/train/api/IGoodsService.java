package org.lee.rocket.train.api;

import com.baomidou.mybatisplus.extension.service.IService;
import org.lee.rocket.train.common.model.Result;
import org.lee.rocket.train.service.entity.Goods;
import org.lee.rocket.train.service.entity.GoodsStocksLog;

/**
 * <p>
 * 商品表 服务类
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-03
 */
public interface IGoodsService extends IService<Goods> {

    /**
     * 根据商品 ID 查询商品信息
     *
     * @param goodsId 商品 ID
     */
    Goods findById(Long goodsId);

    /**
     * 扣减库存
     *
     * @param goodsStocksLog 扣减库存日志
     */
    Result reduceStock(GoodsStocksLog goodsStocksLog);
}
