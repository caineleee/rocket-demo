package org.lee.rocket.train.common.filter;

import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;
import org.lee.rocket.train.common.context.UserContext;

/**
 * Dubbo Consumer 端 Filter - 自动传递用户上下文
 * 
 * 【作用】
 * 当微服务 A 通过 Dubbo 调用微服务 B 时，自动将当前用户信息（userId、userName）
 * 从 UserContext（ThreadLocal）中读取，并放入 Dubbo Attachment（隐式传参）。
 * 
 * 【执行流程】
 * 1. 用户请求 → Gateway → order-service（UserInfoInterceptor 设置 UserContext）
 * 2. order-service 调用 coupon-service（DubboUserContextConsumerFilter 读取 UserContext）
 * 3. 用户信息放入 Dubbo Attachment（隐式传参，类似 HTTP Header）
 * 4. coupon-service 接收请求（DubboUserContextProviderFilter 从 Attachment 读取并设置 UserContext）
 * 
 * 【@Activate 注解说明】
 * - group = "consumer"：只在 Consumer 端激活
 * - order = -1：执行顺序，数字越小越先执行
 * 
 * 【注意事项】
 * 1. UserContext 必须在当前线程中存在（异步调用需要手动传递）
 * 2. 被调用方服务通过 DubboUserContextProviderFilter 自动从 Attachment 读取用户信息
 * 
 * @see org.lee.rocket.train.common.context.UserContext
 * @see org.lee.rocket.train.common.filter.DubboUserContextProviderFilter
 */
@Activate(group = "consumer", order = -1)
public class DubboUserContextConsumerFilter implements Filter {
    
    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        // 从 UserContext 中获取当前用户信息
        Long userId = UserContext.getUserId();
        String userName = UserContext.getUserName();
        
        // 如果用户信息存在，放入 Dubbo Attachment（隐式传参）
        if (userId != null) {
            invocation.setAttachment("X-User-Id", String.valueOf(userId));
        }
        if (userName != null) {
            invocation.setAttachment("X-User-Name", userName);
        }
        
        // 继续执行调用链
        return invoker.invoke(invocation);
    }
}
