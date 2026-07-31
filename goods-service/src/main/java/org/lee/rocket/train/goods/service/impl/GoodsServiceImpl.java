package org.lee.rocket.train.goods.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboService;
import org.lee.rocket.train.common.annotation.RedisIncr;
import org.lee.rocket.train.common.annotation.FailStrategy;
import org.lee.rocket.train.common.constant.code.ResultCode;
import org.lee.rocket.train.common.exception.CastException;
import org.lee.rocket.train.common.model.Result;
import org.lee.rocket.train.service.entity.GoodsStocksLog;
import org.lee.rocket.train.api.IGoodsService;
import org.lee.rocket.train.service.entity.Goods;
import org.lee.rocket.train.goods.mapper.GoodsMapper;
import org.lee.rocket.train.api.IGoodsStocksLogService;
import org.springframework.transaction.annotation.Transactional;

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
     *
     * @param goodsId 商品 ID
     * @return 商品信息
     */
    @Override
    public Goods findById(Long goodsId) {
        if (goodsId == null) {
            CastException.cast(ResultCode.REQUEST_PARAMETER_VALID);
        }
        return getById(goodsId);
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
     * <p>
     * 【修复内容】
     * 1. 加 @Transactional(rollbackFor = Exception.class)：扣库存 + 写日志两步操作必须原子
     *    注意：@RedisIncr 的 Redis 扣减在事务外执行（AOP 在方法前），
     *    如果 DB 失败 Redis 不会自动回滚——这是已知的 Redis/DB 一致性问题，
     *    完整修复需要在 catch 中补偿 Redis，当前先保证 DB 层原子性
     * 2. 删除 @SuppressWarnings("null")，删除冗余的 goods == null 判断（已在上面检查过）
     * 3. 修复入参污染：不再修改入参 goodsStocksLog 的 goodsNumber（原代码 setGoodsNumber(-(...))
     *    会把调用方对象的字段改成负数，调用方继续使用会拿到错误值），改用局部变量写日志
     * 4. findById 改用 getById，避免 query().one() 抛 TooManyResultsException
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
    @Transactional(rollbackFor = Exception.class)
    @Override
    public Result<?> reduceStock(GoodsStocksLog goodsStocksLog) {
        // 参数校验
        if (goodsStocksLog.getGoodsId() == null
                || goodsStocksLog.getOrderId() == null
                || goodsStocksLog.getGoodsNumber() == null
                || goodsStocksLog.getGoodsNumber() <= 0) {
            CastException.cast(ResultCode.REQUEST_PARAMETER_VALID);
        }
        Goods goods = getById(goodsStocksLog.getGoodsId());
        if (goods == null) {
            CastException.cast(ResultCode.GOODS_NO_EXIST);
        }

        // 校验库存是否充足
        if (goods.getGoodsNumber() < goodsStocksLog.getGoodsNumber()) {
            CastException.cast(ResultCode.GOODS_NUM_NOT_ENOUGH);
        }
        // 减去库存
        goods.setGoodsNumber(goods.getGoodsNumber() - goodsStocksLog.getGoodsNumber());
        boolean updateSuccess = updateById(goods);
        if (!updateSuccess) {
            CastException.cast(ResultCode.REDUCE_GOODS_NUM_FAIL);
        }

        // 记录库存操作日志
        // 【修复】新建日志对象，不修改入参 goodsStocksLog 的 goodsNumber
        // 原代码 goodsStocksLog.setGoodsNumber(-(goodsStocksLog.getGoodsNumber())) 会污染入参，
        // 调用方（如 MQ 消费者）继续使用该对象时会拿到错误的负值
        GoodsStocksLog logEntry = new GoodsStocksLog();
        logEntry.setGoodsId(goodsStocksLog.getGoodsId());
        logEntry.setOrderId(goodsStocksLog.getOrderId());
        logEntry.setGoodsNumber(-goodsStocksLog.getGoodsNumber()); // 负数表示扣减
        logEntry.setLogTime(LocalDateTime.now());
        boolean saveSuccess = goodsStocksLogService.save(logEntry);
        if (!saveSuccess) {
            CastException.cast(ResultCode.REDUCE_GOODS_NUM_EMPTY);
        }

        return Result.success();
    }
}
