package org.lee.rocket.train.goods.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.dubbo.config.annotation.DubboService;
import org.lee.rocket.train.goods.mapper.GoodsStocksLogMapper;
import org.lee.rocket.train.service.entity.GoodsStocksLog;
import org.lee.rocket.train.api.IGoodsStocksLogService;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 订单商品日志表 服务实现类
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-22
 */
@Service // 暴露服务(本地 bean)
@DubboService(interfaceClass = IGoodsStocksLogService.class)  // 同时暴露远程服务
public class GoodsStocksLogServiceImpl extends ServiceImpl<GoodsStocksLogMapper, GoodsStocksLog> implements IGoodsStocksLogService {

}
