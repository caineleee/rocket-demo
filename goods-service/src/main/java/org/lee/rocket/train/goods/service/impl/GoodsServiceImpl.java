package org.lee.rocket.train.goods.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboService;
import org.lee.rocket.train.common.constant.ShopCode;
import org.lee.rocket.train.common.exception.CastException;
import org.lee.rocket.train.common.model.Result;
import org.lee.rocket.train.service.entity.GoodsStocksLog;
import org.lee.rocket.train.serviceapi.IGoodsService;
import org.lee.rocket.train.service.entity.Goods;
import org.lee.rocket.train.goods.mapper.GoodsMapper;
import org.lee.rocket.train.serviceapi.IGoodsStocksLogService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * <p>
 * 商品表 服务实现类
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-03
 */
@Service
@DubboService(interfaceClass = IGoodsService.class)
public class GoodsServiceImpl extends ServiceImpl<GoodsMapper, Goods> implements IGoodsService {

    @Resource
    private IGoodsStocksLogService goodsStocksLogService;

    /**
     * 根据商品 ID 查询商品信息
     *
     * @param goodsId 商品 ID
     */
    @Override
    public Goods findById(Long goodsId) {
        // 校验订单是否存在
        if (goodsId == null) {
            CastException.cast(ShopCode.REQUEST_PARAMETER_VALID);
        }

        return query().eq("goods_id", goodsId).one();
    }

    /**
     * 扣减库存
     * @param goodsStocksLog 扣减库存日志
     * @return Result
     */
    @Override
    public Result reduceStock(GoodsStocksLog goodsStocksLog) {
        // 参数校验
        if (goodsStocksLog.getGoodsId() == null
                || goodsStocksLog.getOrderId() == null
                || goodsStocksLog.getGoodsNumber() == null
                || goodsStocksLog.getGoodsNumber() <= 0) {
            CastException.cast(ShopCode.REQUEST_PARAMETER_VALID);
        }
        Goods goods = query().eq("goods_id", goodsStocksLog.getGoodsId()).one();
        if (goods == null) {
            CastException.cast(ShopCode.GOODS_NO_EXIST);
        }

        // 校验库存是否充足
        if (goods.getGoodsNumber() < goodsStocksLog.getGoodsNumber()) {
            CastException.cast(ShopCode.GOODS_NUM_NOT_ENOUGH);
        }
        // 减去库存
        goods.setGoodsNumber(goods.getGoodsNumber() - goodsStocksLog.getGoodsNumber());
        boolean updateSuccess = updateById(goods);
        if (!updateSuccess) {
            CastException.cast(ShopCode.REDUCE_GOODS_NUM_FAIL);
        }

        // 记录库存操作日志
        goodsStocksLog.setGoodsNumber(-(goodsStocksLog.getGoodsNumber()));
        goodsStocksLog.setLogTime(LocalDateTime.now());
        boolean saveSuccess = goodsStocksLogService.save(goodsStocksLog);
        if (!saveSuccess) {
            CastException.cast(ShopCode.REDUCE_GOODS_NUM_EMPTY);
        }

        return new Result(ShopCode.SUCCESS);
    }
}
