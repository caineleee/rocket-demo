package org.lee.rocket.train.common.listener;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 在线人数统计监听器
 *
 * 【为什么需要统计在线人数？】
 * 在线人数是衡量系统活跃度的重要指标，常用于：
 * - 监控系统负载：在线人数过多可能导致系统压力过大
 * - 业务分析：了解用户活跃时间段，优化运营策略
 * - 功能限制：某些功能可能限制同时在线人数（如直播间）
 *
 * 【监听的事件】
 * HttpSessionListener 监听的是 Session 级别的创建和销毁事件：
 * - sessionCreated()：Session 创建时触发（用户第一次访问）
 * - sessionDestroyed()：Session 销毁时触发（用户注销或 Session 过期）
 *
 * 【Session 的生命周期】
 * 1. 用户第一次访问应用时，Tomcat 会创建一个 HttpSession 对象
 * 2. Session 会分配一个唯一的 sessionId，通常通过 Cookie 发送给浏览器
 * 3. 浏览器后续请求会携带 sessionId，Tomcat 根据 sessionId 找到对应的 Session
 * 4. 如果用户长时间不访问（默认 30 分钟），Session 会过期并被销毁
 * 5. 用户主动注销时，也可以手动销毁 Session
 *
 * 【线程安全】
 * 多个用户可能同时访问，导致 sessionCreated() 和 sessionDestroyed() 并发执行。
 * 必须使用线程安全的计数器。
 *
 * 【为什么使用 AtomicInteger？】
 * - 线程安全：AtomicInteger 使用 CAS（Compare-And-Swap）算法保证原子性
 * - 高性能：相比 synchronized，AtomicInteger 无锁设计，性能更好
 * - 简单易用：提供 incrementAndGet()、decrementAndGet() 等原子操作
 *
 * 【分布式系统的挑战】
 * 在单机环境下，AtomicInteger 可以准确统计在线人数。
 * 但在分布式环境下（多个 Tomcat 实例），每个实例都有自己的 AtomicInteger，
 * 无法统计全局在线人数。
 *
 * 【大厂做法】
 * - 阿里：使用 Redis 的 INCR/DECR 命令统计在线人数
 * - 美团：使用 Redis Set 存储 sessionId，通过 SCARD 获取在线人数
 * - 字节跳动：使用 Redis + 定时任务，定期清理过期 Session
 *
 * 【本实现】
 * 使用 AtomicInteger 统计单机在线人数。
 * 同时将在线人数存储到 ServletContext 中，供其他组件使用。
 */
public class SessionListener implements HttpSessionListener {

    /**
     * 在线人数计数器
     * 
     * 【为什么是 static？】
     * static 变量属于类，不属于某个实例。
     * 无论创建多少个 SessionListener 实例，ONLINE_COUNT 都是同一个变量。
     * 
     * 【初始值】
     * 初始值为 0，表示应用启动时没有在线用户。
     */
    private static final AtomicInteger ONLINE_COUNT = new AtomicInteger(0);

    /**
     * Session 创建时触发
     * 
     * 【执行时机】
     * 当用户第一次访问应用时，Tomcat 会创建一个 HttpSession 对象，
     * 此时会触发 sessionCreated() 方法。
     * 
     * 【处理逻辑】
     * 1. 在线人数 +1
     * 2. 更新 ServletContext 中的在线人数（供其他组件使用）
     * 3. 打印日志
     *
     * @param se HttpSessionEvent 对象，可以获取 HttpSession
     */
    @Override
    public void sessionCreated(HttpSessionEvent se) {
        // 在线人数 +1
        // incrementAndGet() 是原子操作，先加 1，再返回新值
        int currentCount = ONLINE_COUNT.incrementAndGet();
        
        // 更新 ServletContext 中的在线人数
        ServletContext context = se.getSession().getServletContext();
        context.setAttribute("ONLINE_COUNT", currentCount);
        
        System.out.println("[SessionListener] Session 创建 | sessionId=" 
            + se.getSession().getId() + " | 当前在线人数=" + currentCount);
    }

    /**
     * Session 销毁时触发
     * 
     * 【执行时机】
     * Session 销毁有两种情况：
     * 1. Session 过期：用户长时间不访问（默认 30 分钟）
     * 2. 手动销毁：调用 session.invalidate()（如用户注销）
     * 
     * 【处理逻辑】
     * 1. 在线人数 -1
     * 2. 更新 ServletContext 中的在线人数
     * 3. 打印日志
     *
     * @param se HttpSessionEvent 对象
     */
    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
        // 在线人数 -1
        // decrementAndGet() 是原子操作，先减 1，再返回新值
        int currentCount = ONLINE_COUNT.decrementAndGet();
        
        // 防止计数器变为负数
        // 理论上不会出现这种情况，但为了安全起见，做一下保护
        if (currentCount < 0) {
            ONLINE_COUNT.set(0);
            currentCount = 0;
        }
        
        // 更新 ServletContext 中的在线人数
        ServletContext context = se.getSession().getServletContext();
        context.setAttribute("ONLINE_COUNT", currentCount);
        
        System.out.println("[SessionListener] Session 销毁 | sessionId=" 
            + se.getSession().getId() + " | 当前在线人数=" + currentCount);
    }

    /**
     * 获取当前在线人数
     * 
     * 【用途】
     * 提供一个静态方法，方便其他组件获取在线人数。
     * 例如：Controller 可以通过 SessionListener.getOnlineCount() 获取在线人数。
     * 
     * @return 当前在线人数
     */
    public static int getOnlineCount() {
        return ONLINE_COUNT.get();
    }
}
