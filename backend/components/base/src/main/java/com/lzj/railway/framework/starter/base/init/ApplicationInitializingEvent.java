package com.lzj.railway.framework.starter.base.init;

import org.springframework.context.ApplicationEvent;

/**
 * Published once after the Spring Boot application is ready.
 *
 * <p>Components and services can listen for this event to run initialization
 * that depends on a fully started application context.</p>
 */
public final class ApplicationInitializingEvent extends ApplicationEvent {

    public ApplicationInitializingEvent(Object source) {
        super(source);
    }
}
