package org.lee.rocket.train.common.exception;

import lombok.Getter;
import org.lee.rocket.train.common.constant.code.ResultCode;

/**
 * 业务异常
 * <p>
 * 状态码与响应码分离后，code 字段存的是 ResultCode 的响应码（不写库）。
 * message 直接复用父类 {@link RuntimeException#getMessage()}，不重复定义字段。
 *
 * @author lihongliang
 */
@Getter
public class CustomerException extends RuntimeException {

    /** 响应码（来自 ResultCode，不写库） */
    private final Integer code;

    /**
     * 推荐构造（基于 ResultCode）
     */
    public CustomerException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }
}
