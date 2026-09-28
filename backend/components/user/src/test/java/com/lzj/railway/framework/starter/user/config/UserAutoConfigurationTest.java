package com.lzj.railway.framework.starter.user.config;

import com.lzj.railway.framework.starter.user.filter.UserContextFilter;
import com.lzj.railway.framework.starter.user.token.JwtTokenGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class UserAutoConfigurationTest {

    private static final String SECRET = "railway-user-component-secret-32-bytes";

    private final WebApplicationContextRunner servletContextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(UserAutoConfiguration.class));

    private final ApplicationContextRunner nonWebContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(UserAutoConfiguration.class));

    @Test
    void shouldStayDisabledWithoutJwtSecret() {
        servletContextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(JwtTokenGenerator.class);
            assertThat(context).doesNotHaveBean(UserContextFilter.class);
        });
    }

    @Test
    void shouldCreateUserBeansWhenJwtSecretIsConfigured() {
        servletContextRunner
                .withPropertyValues(
                        "railway.user.jwt.secret=" + SECRET,
                        "railway.user.jwt.expiration=30m",
                        "railway.user.jwt.issuer=railway-test"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(UserProperties.class);
                    assertThat(context).hasSingleBean(JwtTokenGenerator.class);
                    assertThat(context).hasSingleBean(UserContextFilter.class);
                });
    }

    @Test
    void shouldCreateTokenGeneratorWithoutServletFilterInNonWebApplication() {
        nonWebContextRunner
                .withPropertyValues("railway.user.jwt.secret=" + SECRET)
                .run(context -> {
                    assertThat(context).hasSingleBean(UserProperties.class);
                    assertThat(context).hasSingleBean(JwtTokenGenerator.class);
                    assertThat(context).doesNotHaveBean(UserContextFilter.class);
                });
    }

    @Test
    void shouldBackOffForCustomTokenGenerator() {
        UserProperties.Jwt properties = new UserProperties.Jwt();
        properties.setSecret(SECRET);
        JwtTokenGenerator customGenerator = new JwtTokenGenerator(properties);

        servletContextRunner
                .withPropertyValues("railway.user.jwt.secret=" + SECRET)
                .withBean(JwtTokenGenerator.class, () -> customGenerator)
                .run(context -> assertThat(context.getBean(JwtTokenGenerator.class))
                        .isSameAs(customGenerator));
    }
}
