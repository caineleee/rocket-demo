package org.lee.rocket.train.common.exception;

import lombok.Getter;
import org.lee.rocket.train.common.constant.ShopCode;

/**
 * @ClassName CustomerException
 * @Description
 * @Author lihongliang
 * @Date 2026/6/5 09:02
 * @Version 1.0
 */
public class CustomerException extends RuntimeException{

    @Getter
    private final ShopCode shopCode;

    public CustomerException(ShopCode shopCode) {
        super(shopCode != null ? shopCode.getMessage() : "未知错误");
        this.shopCode = shopCode;
    }
}
