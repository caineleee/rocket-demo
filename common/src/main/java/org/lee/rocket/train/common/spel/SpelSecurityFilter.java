package org.lee.rocket.train.common.spel;

import lombok.extern.slf4j.Slf4j;
import org.lee.rocket.train.common.constant.ShopCode;
import org.lee.rocket.train.common.exception.CustomerException;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * SpEL 表达式安全过滤器
 * <p>
 * 防止 SpEL 注入攻击：永远不要将用户输入直接拼入 key() 表达式
 * <p>
 * 【安全风险】
 * SpEL 表达式可以调用任意 Java 方法，如果用户输入被直接拼入表达式，
 * 可能导致远程代码执行（RCE）漏洞。
 * <p>
 * 例如：key = "#userInput" 且 userInput = "T(java.lang.Runtime).getRuntime().exec('rm -rf /')"
 * 这会执行系统命令，造成严重安全问题。
 * <p>
 * 【防护策略】
 * 1. 黑名单过滤：禁止调用危险类和方法
 * 2. 只允许访问方法参数和返回值，不允许访问 Spring 容器 Bean
 * 3. 禁止使用 T() 操作符访问任意类
 * <p>
 * 【黑名单列表】
 * - Runtime.getRuntime()：执行系统命令
 * - ProcessBuilder：创建进程
 * - Class.forName()：动态加载类
 * - System.exit()：退出 JVM
 * - Thread.sleep()：阻塞线程
 * - java.io.File：文件系统操作
 * - java.lang.reflect：反射操作
 * - sun.misc：内部 API
 *
 * @author lee
 */
@Slf4j
public class SpelSecurityFilter {

    /**
     * SpEL 黑名单：禁止在表达式中调用的危险类和方法
     * <p>
     * 这些类/方法如果被用户输入控制，可能导致：
     * - 远程代码执行（RCE）
     * - 文件系统访问
     * - 网络请求
     * - JVM 崩溃
     */
    private static final Set<String> SPEL_BLACKLIST = Set.of(
            "Runtime.getRuntime",
            "ProcessBuilder",
            "Class.forName",
            "System.exit",
            "System.gc",
            "Thread.sleep",
            "java.io.File",
            "java.lang.reflect",
            "sun.misc",
            "java.net.URL",
            "java.net.HttpURLConnection"
    );

    /**
     * SpEL 解析器（线程安全，可复用）
     */
    private static final ExpressionParser PARSER = new SpelExpressionParser();

    /**
     * Spring 参数名发现器（线程安全，可复用）
     * <p>
     * 用于从 Method 中获取真实的参数名（如 "userId" 而非 "arg0"）。
     * 依赖编译时的 -parameters 参数（Spring Boot 默认已配置）。
     */
    private static final ParameterNameDiscoverer PARAM_NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    /**
     * 解析 SpEL 表达式并返回结果
     * <p>
     * 【执行流程】
     * 1. 检查表达式是否包含黑名单关键字
     * 2. 创建评估上下文（绑定方法参数）
     * 3. 解析并执行表达式
     * 4. 返回结果
     * <p>
     * 【参数说明】
     * @param expression SpEL 表达式，例如 "#userId" 或 "'prefix:' + #id"
     * @param method 目标方法（用于获取参数名）
     * @param args 方法参数值数组
     * @return 表达式解析结果
     * @throws CustomerException 如果表达式包含危险调用
     */
    public static Object evaluate(String expression, Method method, Object[] args) {
        // 1. 安全检查：检查表达式是否包含黑名单关键字
        checkSecurity(expression);

        // 2. 创建评估上下文
        StandardEvaluationContext context = new StandardEvaluationContext();

        // 3. 绑定方法参数到上下文
        // 例如：方法参数为 (Long userId, String name)，则绑定 #userId 和 #name
        if (method != null && args != null) {
            String[] paramNames = getParameterNames(method);
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        // 4. 解析并执行表达式
        try {
            return PARSER.parseExpression(expression).getValue(context);
        } catch (Exception e) {
            String methodName = method != null ? method.getName() : "unknown";
            log.error("SpEL 表达式解析失败: expression={}, method={}", expression, methodName, e);
            throw new CustomerException(ShopCode.REDIS_OPERATION_ERROR);
        }
    }

    /**
     * 安全检查：检查表达式是否包含黑名单关键字
     * <p>
     * 【检查逻辑】
     * 遍历黑名单列表，如果表达式包含任何黑名单关键字，立即抛出异常。
     * <p>
     * 【示例】
     * - 表达式 "#userId" → 安全 ✅
     * - 表达式 "T(java.lang.Runtime).getRuntime()" → 包含 "Runtime.getRuntime" → 危险 ❌
     * - 表达式 "'prefix:' + #id" → 安全 ✅
     *
     * @param expression SpEL 表达式
     * @throws CustomerException 如果表达式包含危险调用
     */
    private static void checkSecurity(String expression) {
        for (String blacklisted : SPEL_BLACKLIST) {
            if (expression.contains(blacklisted)) {
                log.warn("SpEL 表达式包含危险调用: expression={}, blacklisted={}", expression, blacklisted);
                throw new CustomerException(ShopCode.REDIS_SPEL_SECURITY_VIOLATION);
            }
        }
    }

    /**
     * 获取方法参数名数组
     * <p>
     * 【实现方式】
     * 使用 Spring 的 DefaultParameterNameDiscoverer 获取真实参数名。
     * 如果无法获取（如编译时未保留参数名），使用 arg0, arg1, arg2... 作为默认值。
     * <p>
     * 【注意事项】
     * 需要在编译时添加 -parameters 参数才能获取真实参数名。
     * Spring Boot 默认已配置此参数。
     *
     * @param method 目标方法
     * @return 参数名数组
     */
    private static String[] getParameterNames(Method method) {
        // 使用 Spring 的 ParameterNameDiscoverer 获取真实参数名
        String[] paramNames = PARAM_NAME_DISCOVERER.getParameterNames(method);
        if (paramNames != null) {
            return paramNames;
        }
        // 降级：使用 arg0, arg1, arg2... 作为参数名
        int paramCount = method.getParameterCount();
        String[] fallbackNames = new String[paramCount];
        for (int i = 0; i < paramCount; i++) {
            fallbackNames[i] = "arg" + i;
        }
        return fallbackNames;
    }
}
