package com.lzj.railway.framework.starter.idempotent.key;

import com.lzj.railway.framework.starter.idempotent.annotation.Idempotent;
import com.lzj.railway.framework.starter.idempotent.enums.IdempotentScene;
import com.lzj.railway.framework.starter.idempotent.enums.IdempotentType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotentKeyResolverTest {

    private final IdempotentKeyResolver resolver = new IdempotentKeyResolver("railway:idempotent");

    @Test
    void shouldGenerateStableDigestForParamType() throws Exception {
        Method method = SampleService.class.getDeclaredMethod("submit", OrderRequest.class);
        Idempotent annotation = method.getAnnotation(Idempotent.class);

        String first = resolver.resolve(method, new Object[]{new OrderRequest("O-100")}, annotation);
        String second = resolver.resolve(method, new Object[]{new OrderRequest("O-100")}, annotation);
        String different = resolver.resolve(method, new Object[]{new OrderRequest("O-101")}, annotation);

        assertThat(first)
                .isEqualTo(second)
                .startsWith("railway:idempotent:rest_api:")
                .isNotEqualTo(different)
                .doesNotContain("O-100");
    }

    @Test
    void shouldResolveSpelWithBusinessPrefix() throws Exception {
        Method method = SampleService.class.getDeclaredMethod("submitByBusinessKey", OrderRequest.class);

        String key = resolver.resolve(
                method,
                new Object[]{new OrderRequest("O-200")},
                method.getAnnotation(Idempotent.class)
        );

        assertThat(key).isEqualTo("railway:idempotent:rest_api:order:submit:O-200");
    }

    @Test
    void shouldResolveTokenFromProvider() throws Exception {
        IdempotentKeyResolver tokenResolver = new IdempotentKeyResolver(
                "railway:idempotent",
                () -> "TOKEN-300"
        );
        Method method = SampleService.class.getDeclaredMethod("submitByToken");

        String key = tokenResolver.resolve(
                method,
                new Object[0],
                method.getAnnotation(Idempotent.class)
        );

        assertThat(key).isEqualTo("railway:idempotent:rest_api:order:token:TOKEN-300");
    }

    @Test
    void shouldRejectMissingSpelAndMqTokenType() throws Exception {
        Method missingSpel = SampleService.class.getDeclaredMethod("missingSpel");
        Method mqToken = SampleService.class.getDeclaredMethod("mqToken");

        assertThatThrownBy(() -> resolver.resolve(
                missingSpel,
                new Object[0],
                missingSpel.getAnnotation(Idempotent.class)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("必须配置");
        assertThatThrownBy(() -> resolver.resolve(
                mqToken,
                new Object[0],
                mqToken.getAnnotation(Idempotent.class)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("只支持 REST_API");
    }

    private static class SampleService {

        @Idempotent
        void submit(OrderRequest request) {
        }

        @Idempotent(
                type = IdempotentType.SPEL,
                key = "#request.orderId()",
                uniqueKeyPrefix = ":order:submit:"
        )
        void submitByBusinessKey(OrderRequest request) {
        }

        @Idempotent(type = IdempotentType.TOKEN, uniqueKeyPrefix = "order:token")
        void submitByToken() {
        }

        @Idempotent(type = IdempotentType.SPEL)
        void missingSpel() {
        }

        @Idempotent(scene = IdempotentScene.MQ, type = IdempotentType.TOKEN)
        void mqToken() {
        }
    }

    private record OrderRequest(String orderId) {
    }
}
