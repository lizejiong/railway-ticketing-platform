package com.lzj.railway.framework.starter.base.safe;

import org.springframework.beans.factory.InitializingBean;

/**
 * Enables Fastjson2 safe mode to disable auto type resolution.
 */
public final class FastJsonSafeMode implements InitializingBean {

    public static final String SAFE_MODE_PROPERTY = "fastjson2.parser.safeMode";

    @Override
    public void afterPropertiesSet() {
        System.setProperty(SAFE_MODE_PROPERTY, Boolean.TRUE.toString());
    }
}
