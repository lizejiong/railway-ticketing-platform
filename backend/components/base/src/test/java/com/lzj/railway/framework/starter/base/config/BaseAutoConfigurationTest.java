package com.lzj.railway.framework.starter.base.config;

import com.lzj.railway.framework.starter.base.init.ApplicationInitializingPostProcessor;
import com.lzj.railway.framework.starter.base.safe.FastJsonSafeMode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class BaseAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(BaseAutoConfiguration.class));

    private String originalSafeMode;

    @BeforeEach
    void rememberSafeModeProperty() {
        originalSafeMode = System.getProperty(FastJsonSafeMode.SAFE_MODE_PROPERTY);
    }

    @AfterEach
    void restoreSafeModeProperty() {
        if (originalSafeMode == null) {
            System.clearProperty(FastJsonSafeMode.SAFE_MODE_PROPERTY);
        } else {
            System.setProperty(FastJsonSafeMode.SAFE_MODE_PROPERTY, originalSafeMode);
        }
    }

    @Test
    void shouldEnableFastJsonSafeModeByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(FastJsonSafeMode.class);
            assertThat(context).hasSingleBean(ApplicationInitializingPostProcessor.class);
            assertThat(System.getProperty(FastJsonSafeMode.SAFE_MODE_PROPERTY)).isEqualTo("true");
        });
    }

    @Test
    void shouldAllowSafeModeToBeDisabled() {
        contextRunner
                .withPropertyValues("railway.fastjson.safe-mode=false")
                .run(context -> assertThat(context).doesNotHaveBean(FastJsonSafeMode.class));
    }
}
