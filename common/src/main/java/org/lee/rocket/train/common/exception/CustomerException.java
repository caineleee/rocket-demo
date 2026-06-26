package org.lee.rocket.train.common.exception;

import org.lee.rocket.train.common.constant.ShopCode;

/**
 * @ClassName CustomerException
 * @Description
 * @Author lihongliang
 * @Date 2026/6/5 09:02
 * @Version 1.0
 */
public class CustomerException extends RuntimeException{

    private ShopCode shopCode;

    public CustomerException(ShopCode shopCode) { this.shopCode = shopCode; }
}
