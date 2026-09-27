package com.lzj.railway.framework.starter.base.constant;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Execution order of filters shared by railway services.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FilterOrderConstant {

    public static final int USER_TRANSMIT_FILTER_ORDER = 100;

}
