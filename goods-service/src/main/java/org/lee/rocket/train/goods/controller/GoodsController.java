package org.lee.rocket.train.goods.controller;

import jakarta.annotation.Resource;
import org.lee.rocket.train.api.IGoodsService;
import org.lee.rocket.train.common.model.Result;
import org.lee.rocket.train.service.entity.Goods;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 商品表 前端控制器
 * </p>
 *
 * @author CodeGenerator
 * @since 2026-06-03
 */
@RestController
@RequestMapping("/goods")
public class GoodsController {

    @Resource
    private IGoodsService goodsService;

    /**
     * 根据商品 ID 查询商品信息（HTTP GET 接口）
     * <p>
     * 【OpenFeign 示例】
     * 这是一个 REST API 接口，供其他服务通过 OpenFeign HTTP 调用。
     * <p>
     * 【与 Dubbo 的对比】
     * - Dubbo 接口：IGoodsService.findById(Long goodsId)
     *   实现：GoodsServiceImpl.findById(Long goodsId)
     *   调用方式：@DubboReference 注入（RPC 协议）
     * <p>
     * - HTTP 接口：本方法 findById(Long goodsId)
     *   调用方式：@FeignClient 注入（HTTP 协议）
     *   示例：order-service 中的 GoodsFeignClient.findById()
     * <p>
     * 【使用场景】
     * 1. 跨语言服务调用（如 Python、Node.js 服务调用 Java 服务）
     * 2. 调用外部第三方 HTTP 服务
     * 3. 需要更灵活的 HTTP 请求控制（如自定义 Header、Query 参数）
     * <p>
     * 【注意事项】
     * 1. 返回值必须使用 Result<T> 包装，与 Feign Client 接口定义一致
     * 2. 请求路径必须与 Feign Client 的 @GetMapping 路径一致
     * 3. 参数绑定使用 @PathVariable，与 Feign Client 的 @PathVariable 对应
     *
     * @param goodsId 商品 ID
     * @return 商品信息（包含商品名称、价格、库存等）
     */
    @GetMapping("/{id}")
    public Result<Goods> findById(@PathVariable("id") Long goodsId) {
        Goods goods = goodsService.findById(goodsId);
        if (goods != null) {
            return Result.success(goods);
        }
        return Result.error("商品不存在");
    }
}
