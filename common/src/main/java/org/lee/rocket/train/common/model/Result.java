package org.lee.rocket.train.common.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lee.rocket.train.common.constant.ShopCode;

import java.io.Serializable;

/**
 * 通用响应结果
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
     * 响应成功标识
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

    public Result(ShopCode shopCode) {
        this.code = String.valueOf(shopCode.getCode());
        this.success = shopCode.getSuccess();
        this.message = shopCode.getMessage();
        this.data = null;
    }

    /**
     * 成功响应
     */
    public static <T> Result<T> success(T data) {
        return new Result<>("200",true, "操作成功", data);
    }
    
    /**
     * 成功响应（无数据）
     */
    public static <T> Result<T> success() {
        return new Result<>("200", true,"操作成功", null);
    }
    
    /**
     * 失败响应
     */
    public static <T> Result<T> error(String message) {
        return new Result<>("500", false, message, null);
    }
}
