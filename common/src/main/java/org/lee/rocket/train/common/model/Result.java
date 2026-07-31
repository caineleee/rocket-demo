package org.lee.rocket.train.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lee.rocket.train.common.constant.code.ResultCode;

import java.io.Serializable;

/**
 * 通用响应结果
 * <p>
 * 状态码与响应码分离后的调整：
 * - 新增 {@link #Result(ResultCode)} 构造和 {@link #fail(ResultCode)} 工厂方法（推荐）
 * - {@code success} 字段保留以兼容前端协议，但其值由 {@code code == 200} 推导，
 *   不再独立维护，消除原 ShopCode.success 布尔字段语义双关的问题
 *
 * @param <T> 响应数据类型
 * @author lihongliang
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 响应码
     */
    private String code;

    /**
     * 响应成功标识（前端按此判断，值由 code==200 推导）
     */
    private Boolean success;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 基于 ResultCode 构造（推荐）
     */
    public Result(ResultCode rc) {
        this.code = String.valueOf(rc.getCode());
        this.success = (rc == ResultCode.SUCCESS);
        this.message = rc.getMessage();
        this.data = null;
    }

    /**
     * 成功响应（带数据）
     */
    public static <T> Result<T> success(T data) {
        return new Result<>("200", true, "操作成功", data);
    }

    /**
     * 成功响应（无数据）
     */
    public static <T> Result<T> success() {
        return new Result<>("200", true, "操作成功", null);
    }

    /**
     * 失败响应（基于 ResultCode，推荐）
     */
    public static <T> Result<T> fail(ResultCode rc) {
        return new Result<>(rc);
    }

    /**
     * 失败响应（自定义消息）
     */
    public static <T> Result<T> error(String message) {
        return new Result<>("500", false, message, null);
    }

    /**
     * 失败响应（自定义码和消息）
     */
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code.toString(), false, message, null);
    }

    /**
     * 失败响应（默认）
     */
    public static <T> Result<T> error() {
        return new Result<>(ResultCode.FAIL);
    }
}
