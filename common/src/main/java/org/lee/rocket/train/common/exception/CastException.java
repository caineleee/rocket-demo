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
    /**
     * 抛出业务异常
     * 注意：此方法总是抛出异常，永远不会正常返回
     * 返回类型设为 RuntimeException 以便 IDE 能识别出后续代码不可达
     */
    public static RuntimeException cast(ShopCode shopCode) {
        log.error(shopCode.toString());
        throw new CustomerException(shopCode);
    }
}