package org.lee.rocket.train.goods.controller;

import lombok.extern.slf4j.Slf4j;
import org.lee.rocket.train.common.annotation.*;
import org.lee.rocket.train.common.model.Result;
import org.lee.rocket.train.service.entity.Goods;
import org.lee.rocket.train.api.IGoodsService;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis AOP 注解使用示例 Controller
 * <p>
 * 本 Controller 演示了所有 Redis AOP 注解的实际使用场景。
 * 每个接口都展示了不同注解的用法和注意事项。
 * <p>
 * 【测试方式】
 * 启动应用后，使用 Postman 或 curl 调用以下接口：
 * 1. GET  /goods-service/demo/goods/{id}     → 演示 @RedisGet（缓存读取）
 * 2. POST /goods-service/demo/goods           → 演示 @RedisSet（缓存写入）
 * 3. POST /goods-service/demo/stock/{id}      → 演示 @RedisIncr（库存扣减）
 * 4. POST /goods-service/demo/order/{orderNo} → 演示 @RedisLock（分布式锁）
 * 5. DELETE /goods-service/demo/goods/{id}    → 演示 @RedisDel（缓存删除）
 *
 * @author lee
 */
@Slf4j
@RestController
@RequestMapping("/goods-service/demo")
public class RedisAopDemoController {

    @Resource
    private IGoodsService goodsService;

    /**
     * 模拟商品缓存（实际项目中应该从 DB 查询）
     */
    private final Map<Long, Goods> mockGoodsDb = new HashMap<>();

    // ==================== @RedisGet 使用示例 ====================

    /**
     * 查询商品详情（演示 @RedisGet）
     * <p>
     * 【执行流程】
     * 1. 请求进入 → RedisAspect 拦截
     * 2. 解析 SpEL：key = "#id" → 取方法参数 id 的值
     * 3. 从 Redis 读取 Key = "goods:detail:RedisAopDemoController.getGoodsDetail:{id}"
     * 4. 如果缓存命中 → 反序列化为 Goods → 注入到 cachedGoods 参数
     * 5. 如果缓存未命中 → cachedGoods = null → 方法内部走 DB 查询
     * 6. 返回结果
     * <p>
     * 【参数说明】
     * - key = "#id"：SpEL 表达式，引用方法参数 id
     * - prefix = "goods:detail"：Key 前缀
     * - injectParam = "cachedGoods"：将 Redis 值注入到此参数
     * - failStrategy = DB_ONLY：Redis 异常时参数为 null，走 DB 查询
     * <p>
     * 【测试命令】
     * curl http://localhost:8080/goods-service/demo/goods/1
     * <p>
     * 【预期结果】
     * 第一次调用：缓存未命中 → 从 mock DB 查询 → 返回商品
     * 第二次调用：缓存命中 → 直接返回缓存 → 不查 DB
     *
     * @param id 商品 ID
     * @param cachedGoods 从 Redis 注入的缓存商品（可能为 null）
     * @return 商品信息
     */
    @RedisGet(
            key = "#id",
            prefix = "goods:detail",
            injectParam = "cachedGoods",
            failStrategy = FailStrategy.DB_ONLY
    )
    @GetMapping("/goods/{id}")
    public Result<Goods> getGoodsDetail(@PathVariable Long id, Goods cachedGoods) {
        log.info("===== @RedisGet 示例：查询商品详情 =====");
        log.info("商品 ID: {}", id);
        log.info("缓存注入的 Goods: {}", cachedGoods);

        // 如果缓存命中，直接返回
        if (cachedGoods != null) {
            log.info("✅ 缓存命中，直接返回缓存数据");
            return Result.success(cachedGoods);
        }

        // 缓存未命中，从 DB 查询（这里用 mock 数据模拟）
        log.info(" 缓存未命中，从 DB 查询");
        Goods goods = mockGoodsDb.get(id);
        if (goods == null) {
            // 模拟 DB 查询：创建一个 mock 商品
            goods = new Goods();
            goods.setGoodsId(id);
            goods.setGoodsName("示例商品-" + id);
            goods.setGoodsPrice(9900L); // 99 元（单位：分）
            goods.setGoodsNumber(100);
            mockGoodsDb.put(id, goods);
            log.info("从 DB 查询到商品: {}", goods);
        }

        return Result.success(goods);
    }

    // ==================== @RedisSet 使用示例 ====================

    /**
     * 更新商品信息并写入缓存（演示 @RedisSet）
     * <p>
     * 【执行流程】
     * 1. 请求进入 → RedisAspect 拦截
     * 2. 先执行业务方法（更新 DB）
     * 3. 获取返回值（更新后的 Goods）
     * 4. 解析 SpEL：key = "#result.goodsId" → 取返回值的 goodsId
     * 5. 将返回值序列化为 JSON → 写入 Redis
     * 6. 大 Key 检测：如果超过 10KB，打印 WARN 日志
     * 7. 返回结果
     * <p>
     * 【参数说明】
     * - key = "#result.goodsId"：SpEL 表达式，引用返回值的 goodsId
     * - prefix = "goods:detail"：Key 前缀
     * - ttl = 3600：1 小时后过期
     * - maxSize = 10240：大 Key 阈值 10KB
     * - condition = "#result != null"：结果非空时才缓存
     * - failStrategy = FAIL_SAFE：Redis 写入失败不影响业务
     * <p>
     * 【测试命令】
     * curl -X POST http://localhost:8080/goods-service/demo/goods \
     *   -H "Content-Type: application/json" \
     *   -d '{"goodsId":1,"goodsName":"更新后的商品","goodsPrice":19900,"goodsNumber":50}'
     * <p>
     * 【预期结果】
     * 1. 更新 mock DB 中的商品
     * 2. 将更新后的商品写入 Redis（Key = goods:detail:RedisAopDemoController.updateGoods:1）
     * 3. 后续调用 getGoodsDetail(1) 会命中缓存
     *
     * @param goods 更新后的商品信息
     * @return 更新后的商品
     */
    @RedisSet(
            key = "#result.goodsId",
            prefix = "goods:detail",
            ttl = 3600,
            maxSize = 10240,
            condition = "#result != null",
            failStrategy = FailStrategy.FAIL_SAFE
    )
    @PostMapping("/goods")
    public Result<Goods> updateGoods(@RequestBody Goods goods) {
        log.info("===== @RedisSet 示例：更新商品并写入缓存 =====");
        log.info("更新商品: {}", goods);

        // 模拟 DB 更新
        mockGoodsDb.put(goods.getGoodsId(), goods);
        log.info("DB 更新成功");

        // 返回更新后的商品（会被 @RedisSet 自动写入 Redis）
        return Result.success(goods);
    }

    // ==================== @RedisIncr 使用示例 ====================

    /**
     * 扣减库存（演示 @RedisIncr）
     * <p>
     * 【执行流程】
     * 1. 请求进入 → RedisAspect 拦截
     * 2. 解析 SpEL：key = "#id" → 取方法参数 id
     * 3. Redis 执行 INCRBY "goods:stock:RedisAopDemoController.deductStock:{id}" -1
     * 4. 检查扣减后是否低于 minCount（0）
     * 5. 如果低于 0 → FAIL_FAST → 抛异常，阻止扣减
     * 6. 如果正常 → 执行业务方法（扣减 DB 库存）
     * 7. 返回结果
     * <p>
     * 【参数说明】
     * - key = "#id"：SpEL 表达式，引用方法参数 id
     * - prefix = "goods:stock"：Key 前缀
     * - delta = -1：每次扣减 1 件（负数表示减少）
     * - minCount = 0：库存不能低于 0（防止超卖）
     * - failStrategy = FAIL_FAST：库存不足时直接抛异常
     * <p>
     * 【为什么用 @RedisIncr 而不是 @Cacheable？】
     * - @Cacheable 是"读-执行-写"模式，没有原子自增能力
     * - 库存扣减需要 INCR + 阈值判断的原子操作
     * - Redis INCR 是原子操作，天然支持高并发
     * <p>
     * 【测试命令】
     * curl -X POST http://localhost:8080/goods-service/demo/stock/1
     * <p>
     * 【预期结果】
     * 第 1-100 次调用：扣减成功（库存从 100 → 0）
     * 第 101 次调用：库存不足 → 抛异常 "库存不足，无法扣减"
     *
     * @param id 商品 ID
     * @return 扣减结果
     */
    @RedisIncr(
            key = "#id",
            prefix = "goods:stock",
            delta = -1,
            minCount = 0,
            failStrategy = FailStrategy.FAIL_FAST
    )
    @PostMapping("/stock/{id}")
    public Result<String> deductStock(@PathVariable Long id) {
        log.info("===== @RedisIncr 示例：扣减库存 =====");
        log.info("商品 ID: {}", id);

        // 模拟 DB 库存扣减
        Goods goods = mockGoodsDb.get(id);
        if (goods != null) {
            goods.setGoodsNumber(goods.getGoodsNumber() - 1);
            log.info("DB 库存扣减成功，剩余库存: {}", goods.getGoodsNumber());
        }

        return Result.success("库存扣减成功");
    }

    // ==================== @RedisLock 使用示例 ====================

    /**
     * 创建订单（演示 @RedisLock）
     * <p>
     * 【执行流程】
     * 1. 请求进入 → RedisAspect 拦截
     * 2. 解析 SpEL：key = "#orderNo" → 取方法参数 orderNo
     * 3. Redisson 尝试获取锁：tryLock("lock:order:RedisAopDemoController.createOrder:{orderNo}", 3, 10)
     * 4. 如果获取成功 → 执行业务方法（创建订单）
     * 5. 如果获取失败 → FAIL_FAST → 抛异常 "分布式锁获取失败"
     * 6. 无论成功还是异常 → finally 块释放锁
     * <p>
     * 【参数说明】
     * - key = "#orderNo"：SpEL 表达式，引用方法参数 orderNo
     * - prefix = "lock:order"：Key 前缀
     * - waitTime = 3：最多等 3 秒
     * - leaseTime = 10：10 秒后自动释放（防止死锁）
     * - failStrategy = FAIL_FAST：获取锁失败直接抛异常
     * <p>
     * 【为什么用 @RedisLock 而不是 @Cacheable？】
     * - @Cacheable 是缓存语义，没有互斥锁能力
     * - 分布式锁需要 Redisson 的 RLock.tryLock(waitTime, leaseTime)
     * - 涉及等待、续期、释放等完整生命周期
     * <p>
     * 【测试命令】
     * curl -X POST http://localhost:8080/goods-service/demo/order/ORDER-001
     * <p>
     * 【预期结果】
     * 同一 orderNo 的并发请求：只有一个能获取锁，其他请求被拒绝
     * 不同 orderNo 的请求：互不影响，可以并发执行
     *
     * @param orderNo 订单号
     * @return 创建结果
     */
    @RedisLock(
            key = "#orderNo",
            prefix = "lock:order",
            waitTime = 3,
            leaseTime = 10,
            failStrategy = FailStrategy.FAIL_FAST
    )
    @PostMapping("/order/{orderNo}")
    public Result<String> createOrder(@PathVariable String orderNo) {
        log.info("===== @RedisLock 示例：创建订单 =====");
        log.info("订单号: {}", orderNo);

        // 模拟订单创建（耗时操作）
        try {
            Thread.sleep(1000); // 模拟 DB 操作耗时 1 秒
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("订单创建成功: {}", orderNo);
        return Result.success("订单创建成功: " + orderNo);
    }

    // ==================== @RedisDel 使用示例 ====================

    /**
     * 删除商品并清理缓存（演示 @RedisDel）
     * <p>
     * 【执行流程】
     * 1. 请求进入 → RedisAspect 拦截
     * 2. 先执行业务方法（删除 DB 数据）
     * 3. 解析 SpEL：keys = ["#id", "'goods:stock:' + #id"]
     * 4. 删除 Redis Key：
     *    - goods:detail:RedisAopDemoController.deleteGoods:{id}
     *    - goods:stock:{id}
     * 5. 返回结果
     * <p>
     * 【参数说明】
     * - keys = ["#id", "'goods:stock:' + #id"]：支持多个 Key（数组）
     * - prefix = "goods:detail"：Key 前缀
     * - failStrategy = FAIL_SAFE：删除失败不影响业务
     * <p>
     * 【为什么用 @RedisDel 而不是 @CacheEvict？】
     * - @CacheEvict 只能清除 @Cacheable 管理的缓存
     * - 实际业务中需要删除的 Key 可能是计数器、锁等
     * - 这些 Key 根本不是 @Cacheable 管理的
     * <p>
     * 【测试命令】
     * curl -X DELETE http://localhost:8080/goods-service/demo/goods/1
     * <p>
     * 【预期结果】
     * 1. 从 mock DB 删除商品
     * 2. 删除 Redis 中的商品详情缓存
     * 3. 删除 Redis 中的库存计数器
     * 4. 后续调用 getGoodsDetail(1) 会缓存未命中
     *
     * @param id 商品 ID
     * @return 删除结果
     */
    @RedisDel(
            keys = {"#id", "'goods:stock:' + #id"},
            prefix = "goods:detail",
            failStrategy = FailStrategy.FAIL_SAFE
    )
    @DeleteMapping("/goods/{id}")
    public Result<String> deleteGoods(@PathVariable Long id) {
        log.info("===== @RedisDel 示例：删除商品并清理缓存 =====");
        log.info("商品 ID: {}", id);

        // 模拟 DB 删除
        mockGoodsDb.remove(id);
        log.info("DB 删除成功");

        return Result.success("商品删除成功");
    }
}
