package com.lzj.railway.framework.starter.base.config;

import com.lzj.railway.framework.starter.base.init.ApplicationInitializingPostProcessor;
import com.lzj.railway.framework.starter.base.safe.FastJsonSafeMode;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for foundational railway service behavior.
 */
@AutoConfiguration
public class BaseAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ApplicationInitializingPostProcessor applicationInitializingPostProcessor(
            ApplicationContext applicationContext) {
        return new ApplicationInitializingPostProcessor(applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
            prefix = "railway.fastjson",
            name = "safe-mode",
            havingValue = "true",
            matchIfMissing = true
    )
    public FastJsonSafeMode fastJsonSafeMode() {
        return new FastJsonSafeMode();
    }
}
