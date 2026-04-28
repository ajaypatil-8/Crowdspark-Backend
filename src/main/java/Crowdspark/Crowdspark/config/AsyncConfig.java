package Crowdspark.Crowdspark.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.lang.reflect.Method;
import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    @Bean(name = "emailTaskExecutor")
    public Executor emailTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("email-async-");
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() { return emailTaskExecutor(); }

    /**
     * Surfaces @Async exceptions in logs — without this, SMTP failures are silently swallowed.
     * Check Spring Boot logs after clicking "Send verification email" to see the real error.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (Throwable ex, Method method, Object... params) ->
                log.error("[ASYNC-EMAIL] Exception in {}.{}() — {}: {}",
                        method.getDeclaringClass().getSimpleName(),
                        method.getName(),
                        ex.getClass().getSimpleName(),
                        ex.getMessage(), ex);
    }
}