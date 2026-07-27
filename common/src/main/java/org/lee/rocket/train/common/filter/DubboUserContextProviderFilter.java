package org.lee.rocket.train.common.filter;

import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.*;
import org.lee.rocket.train.common.context.UserContext;

/**
 * Dubbo Provider 端 Filter - 自动接收用户上下文
 * 
 * 【作用】
 * 当微服务 B 被微服务 A 通过 Dubbo 调用时，自动从 Dubbo Attachment 中读取用户信息，
 * 并存入 UserContext（ThreadLocal），供业务代码使用。
 * 
 * 【执行流程】
 * 1. order-service 调用 coupon-service（DubboUserContextConsumerFilter 设置 Attachment）
 * 2. coupon-service 接收请求（DubboUserContextProviderFilter 从 Attachment 读取）
 * 3. 用户信息存入 UserContext（ThreadLocal）
 * 4. 业务代码通过 UserContext.getUserId() 获取用户信息
 * 
 * 【@Activate 注解说明】
 * - group = "provider"：只在 Provider 端激活
 * - order = -1：执行顺序，数字越小越先执行
 * 
 * 【注意事项】
 * 1. UserContext 必须在请求结束后清理，防止 ThreadLocal 泄漏
 * 2. 当前实现依赖 ThreadLocal，异步调用需要特殊处理
 * 
 * @see org.lee.rocket.train.common.context.UserContext
 * @see org.lee.rocket.train.common.filter.DubboUserContextConsumerFilter
 */
@Activate(group = "provider", order = -1)
public class DubboUserContextProviderFilter implements Filter {
    
    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        // 从 Dubbo Attachment 中获取用户信息
        String userIdStr = invocation.getAttachment("X-User-Id");
        String userName = invocation.getAttachment("X-User-Name");
        
        // 如果用户信息存在，存入 UserContext
        if (userIdStr != null) {
            try {
                Long userId = Long.valueOf(userIdStr);
                UserContext.setUserId(userId);
            } catch (NumberFormatException e) {
                // 忽略无效的用户 ID
            }
        }
        if (userName != null) {
            UserContext.setUserName(userName);
        }
        
        try {
            // 继续执行调用链
            return invoker.invoke(invocation);
        } finally {
            // 请求完成后清理 UserContext，防止 ThreadLocal 泄漏
            UserContext.clear();
        }
    }
}
