package org.lee.rocket.train.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.lee.rocket.train.common.constant.code.ResultCode;
import org.lee.rocket.train.common.model.Result;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器（统一异常 → {@link Result} JSON）
 * <p>
 * 【为什么需要】
 * 业务代码通过 {@link CastException#cast(ResultCode)} 抛 {@link CustomerException}，
 * {@link org.lee.rocket.train.common.statemachine.StatusTransition#check} 抛 {@link IllegalStateException}。
 * 没有全局处理器时，这些异常直接冒泡到 Spring，前端拿到的是默认 500 错误页
 * （{@code {timestamp,status,error,...}}），无法解析出业务响应码。
 * <p>
 * 【生效范围】
 * 仅在 Servlet（Spring MVC）业务服务生效。Gateway 基于 WebFlux，通过
 * {@code @ConditionalOnWebApplication(type = SERVLET)} 排除，避免在响应式容器加载报错；
 * WebFlux 的异常处理需另写 {@code ErrorWebExceptionHandler}。
 * <p>
 * 【处理顺序】
 * Spring 按"异常类型最匹配优先"选择 Handler，下面从具体到通用排列：
 * 1. {@link CustomerException} —— 业务异常，回带业务响应码
 * 2. {@link MethodArgumentNotValidException} —— @Valid 参数校验失败
 * 3. {@link IllegalStateException} —— 状态机非法流转（编程错误），对外返回统一 500 但记录详细日志
 * 4. {@link Exception} —— 兜底，未知异常统一 500
 *
 * @author lihongliang
 */
@Slf4j
@RestControllerAdvice
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class GlobalExceptionHandler {

    /**
     * 业务异常：回带 {@link ResultCode} 的响应码与消息，前端可按 code 分支处理。
     */
    @ExceptionHandler(CustomerException.class)
    public Result<?> handleCustomerException(CustomerException e) {
        log.error("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.error(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验异常（@Valid 触发）：拼接所有字段错误，方便定位。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", msg);
        return Result.error(ResultCode.REQUEST_PARAMETER_VALID.getCode(), msg);
    }

    /**
     * 状态机非法流转：属于编程错误（不该到达的流转路径被触发）。
     * 对外不暴露内部细节，返回统一 FAIL；对内 log.error 记录，便于开发者排查。
     */
    @ExceptionHandler(IllegalStateException.class)
    public Result<?> handleIllegalState(IllegalStateException e) {
        log.error("状态流转非法（编程错误）: {}", e.getMessage(), e);
        return Result.fail(ResultCode.FAIL);
    }

    /**
     * 兜底：未知异常统一返回 500，避免堆栈泄露给前端。
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error();
    }
}
