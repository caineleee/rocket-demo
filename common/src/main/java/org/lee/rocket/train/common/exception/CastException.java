package org.lee.rocket.train.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.lee.rocket.train.common.constant.ShopCode;

/**
 * @ClassName CastException
 * @Description
 * @Author lihongliang
 * @Date 2026/6/5 09:00
 * @Version 1.0
 */
@Slf4j
public class CastException {
    public static void cast(ShopCode shopCode) {
        log.error(shopCode.toString());
        throw new CustomerException(shopCode);
    }
}