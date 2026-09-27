package com.lzj.railway.framework.starter.base.init;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.support.GenericApplicationContext;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApplicationInitializingPostProcessorTest {

    @Test
    void shouldPublishInitializingEventOnlyOnce() {
        GenericApplicationContext applicationContext = new GenericApplicationContext();
        AtomicInteger eventCount = new AtomicInteger();
        applicationContext.addApplicationListener(event -> {
            if (event instanceof ApplicationInitializingEvent initializingEvent) {
                assertThat(initializingEvent.getSource()).isSameAs(applicationContext);
                eventCount.incrementAndGet();
            }
        });
        applicationContext.refresh();

        try {
            ApplicationReadyEvent readyEvent = mock(ApplicationReadyEvent.class);
            when(readyEvent.getApplicationContext()).thenReturn(applicationContext);
            ApplicationInitializingPostProcessor postProcessor =
                    new ApplicationInitializingPostProcessor(applicationContext);

            postProcessor.onApplicationEvent(readyEvent);
            postProcessor.onApplicationEvent(readyEvent);

            assertThat(eventCount).hasValue(1);
        } finally {
            applicationContext.close();
        }
    }

    @Test
    void shouldIgnoreReadyEventFromAnotherContext() {
        GenericApplicationContext applicationContext = new GenericApplicationContext();
        GenericApplicationContext otherContext = new GenericApplicationContext();
        AtomicInteger eventCount = new AtomicInteger();
        applicationContext.addApplicationListener(event -> {
            if (event instanceof ApplicationInitializingEvent) {
                eventCount.incrementAndGet();
            }
        });
        applicationContext.refresh();
        otherContext.refresh();

        try {
            ApplicationReadyEvent readyEvent = mock(ApplicationReadyEvent.class);
            when(readyEvent.getApplicationContext()).thenReturn(otherContext);

            new ApplicationInitializingPostProcessor(applicationContext).onApplicationEvent(readyEvent);

            assertThat(eventCount).hasValue(0);
        } finally {
            otherContext.close();
            applicationContext.close();
        }
    }
}
