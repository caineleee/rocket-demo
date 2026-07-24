package org.lee.rocket.train.api.feign;

import org.lee.rocket.train.common.model.Result;
import org.lee.rocket.train.service.entity.Goods;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 商品服务 Feign Client（HTTP 调用示例）
 * <p>
 * 【OpenFeign 示例说明】
 * 这是一个 OpenFeign Client 接口，用于通过 HTTP 调用 goods-service 的 REST API。
 * <p>
 * 【与 Dubbo 的对比】
 * - Dubbo 调用：使用 @DubboReference 注入 IGoodsService 接口（RPC 协议，高性能）
 *   示例位置：order-service 中的 OrdersServiceImpl 使用 @DubboReference private IGoodsService goodsService
 * <p>
 * - OpenFeign 调用：使用 @FeignClient 注入 GoodsFeignClient 接口（HTTP 协议，通用性强）
 *   示例位置：本接口 GoodsFeignClient
 * <p>
 * 【关键注解说明】
 * - @FeignClient(name = "goods-service")：
 *   - name：目标服务名称（必须与 goods-service 的 spring.application.name 一致）
 *   - OpenFeign 会从 Nacos 注册中心查找该服务的实例列表
 *   - 负载均衡策略：在 OrderServiceApplication 启动类上通过 @LoadBalancerClient 配置
 *     示例：@LoadBalancerClient(name = "goods-service", configuration = GoodsServiceLoadBalancerConfig.class)
 *     当前使用随机策略（Random），对比 Dubbo 的 @DubboReference(loadbalance = "random")
 * <p>
 * 【使用场景】
 * 1. 调用外部第三方 HTTP 服务（如支付网关、短信服务）
 * 2. 跨语言服务调用（如调用 Python、Node.js 服务）
 * 3. 需要更灵活的 HTTP 请求控制（如自定义 Header、Query 参数）
 * <p>
 * 【注意事项】
 * 1. 目标服务必须注册到 Nacos（配置 spring.cloud.nacos.discovery）
 * 2. 调用方服务必须启用 @EnableFeignClients（在启动类上添加）
 * 3. 接口方法上的 @GetMapping/@PostMapping 必须与目标 Controller 一致
 * 4. 负载均衡配置在调用方启动类上，不在 Feign Client 接口上（因为 service-api 不能依赖 order-service）
 */
@FeignClient(name = "goods-service")
public interface GoodsFeignClient {

    /**
     * 根据商品 ID 查询商品信息（HTTP GET 调用示例）
     * <p>
     * 【对应 Dubbo 示例】
     * - Dubbo 接口：IGoodsService.findById(Long goodsId)
     * - Dubbo 实现：GoodsServiceImpl.findById(Long goodsId)
     * - Dubbo 调用：OrdersServiceImpl 中使用 @DubboReference 注入
     * <p>
     * 【HTTP 接口对应】
     * - 目标 Controller：GoodsController.findById(Long goodsId)
     * - 请求路径：GET /goods-service/goods/{id}
     * - 返回类型：Result<Goods>
     * <p>
     * 【@PathVariable 说明】
     * - 用于绑定 URL 路径中的变量
     * - value = "id"：对应 URL 中的 {id} 占位符
     * - 方法参数 goodsId 会替换 URL 中的 {id}
     *
     * @param goodsId 商品 ID
     * @return 商品信息（包含商品名称、价格、库存等）
     */
    @GetMapping("/goods-service/goods/{id}")
    Result<Goods> findById(@PathVariable("id") Long goodsId);
}
