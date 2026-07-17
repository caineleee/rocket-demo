package org.lee.rocket.train.goods.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboService;
import org.lee.rocket.train.common.annotation.RedisGet;
import org.lee.rocket.train.common.annotation.RedisIncr;
import org.lee.rocket.train.common.annotation.FailStrategy;
import org.lee.rocket.train.common.constant.ShopCode;
import org.lee.rocket.train.common.exception.CastException;
import org.lee.rocket.train.common.model.Result;
import org.lee.rocket.train.service.entity.GoodsStocksLog;
import org.lee.rocket.train.api.IGoodsService;
import org.lee.rocket.train.service.entity.Goods;
import org.lee.rocket.train.goods.mapper.GoodsMapper;
import org.lee.rocket.train.api.IGoodsStocksLogService;

import java.time.LocalDateTime;

/**
 * <p>
 * 商品表 服务实现类
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-03
 */
@DubboService(interfaceClass = IGoodsService.class)
public class GoodsServiceImpl extends ServiceImpl<GoodsMapper, Goods> implements IGoodsService {

    @Resource
    private IGoodsStocksLogService goodsStocksLogService;

    /**
     * 根据商品 ID 查询商品信息
     * <p>
     * 【注意】此方法是 Dubbo 接口方法，签名不能修改（接口契约）
     * 如果需要缓存，建议在 Controller 层或单独的查询方法中使用 @RedisGet
     *
     * @param goodsId 商品 ID
     * @return 商品信息
     */
    @Override
    public Goods findById(Long goodsId) {
        // 校验参数
        if (goodsId == null) {
            CastException.cast(ShopCode.REQUEST_PARAMETER_VALID);
        }

        return query().eq("goods_id", goodsId).one();
    }

    /**
     * 扣减库存
     * <p>
     * 【Redis AOP 使用示例 - @RedisIncr】
     * <p>
     * 使用 @RedisIncr 注解实现库存扣减的原子操作：
     * 1. 方法执行前，Redis 执行 INCRBY 命令（delta = -1，每次扣减 1 件）
     * 2. 如果扣减后库存低于 0（minCount = 0），根据 failStrategy 处理
     * 3. FAIL_FAST：直接抛异常，阻止扣减
     * 4. FAIL_SAFE：记录日志，继续执行（不推荐）
     * <p>
     * 【参数说明】
     * - key = "#goodsStocksLog.goodsId"：SpEL 表达式，引用参数中的商品 ID
     * - prefix = "goods:stock"：Key 前缀，完整 Key = goods:stock:GoodsServiceImpl.reduceStock:{goodsId}
     * - delta = -1：每次扣减 1 件（负数表示减少）
     * - minCount = 0：库存不能低于 0（防止超卖）
     * - failStrategy = FAIL_FAST：库存不足时直接抛异常
     * <p>
     * 【为什么用 @RedisIncr 而不是 @Cacheable？】
     * - @Cacheable 是"读-执行-写"模式，没有原子自增能力
     * - 库存扣减需要 INCR + 阈值判断的原子操作，@Cacheable 无法表达
     * - Redis INCR 是原子操作，天然支持高并发
     * <p>
     * 【注意事项】
     * 1. Redis 中的库存值需要与 DB 保持一致（初始化时从 DB 同步）
     * 2. 如果 Redis 异常，FAIL_FAST 会阻止扣减，保证数据一致性
     * 3. ttl = 0 表示永不过期（库存计数器不能自动过期）
     *
     * @param goodsStocksLog 扣减库存日志
     * @return Result
     */
    @RedisIncr(
            key = "#goodsStocksLog.goodsId",
            prefix = "goods:stock",
            delta = -1,
            minCount = 0,
            failStrategy = FailStrategy.FAIL_FAST
    )
    @SuppressWarnings("null")
    @Override
    public Result<?> reduceStock(GoodsStocksLog goodsStocksLog) {
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
        if (goods== null || goods.getGoodsNumber() < goodsStocksLog.getGoodsNumber()) {
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

        return new Result<>(ShopCode.SUCCESS);
    }
}
