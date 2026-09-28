package com.lzj.railway.framework.starter.web.config;

import com.lzj.railway.framework.starter.web.handler.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class WebAutoConfigurationTest {

    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WebAutoConfiguration.class));

    @Test
    void shouldConfigureExceptionHandlerByDefault() {
        webContextRunner.run(context ->
                assertThat(context).hasSingleBean(GlobalExceptionHandler.class));
    }

    @Test
    void shouldBackOffForApplicationExceptionHandler() {
        GlobalExceptionHandler customHandler = new GlobalExceptionHandler();

        webContextRunner
                .withBean(GlobalExceptionHandler.class, () -> customHandler)
                .run(context -> assertThat(context.getBean(GlobalExceptionHandler.class))
                        .isSameAs(customHandler));
    }

    @Test
    void shouldNotConfigureInNonWebApplication() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(WebAutoConfiguration.class))
                .run(context ->
                        assertThat(context).doesNotHaveBean(GlobalExceptionHandler.class));
    }
}
