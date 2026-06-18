package com.motoyav2.whatsapp.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import java.util.concurrent.Executor;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configura el executor para los @EventListener de WhatsApp.
 *
 * dev  → SyncTaskExecutor: handlers corren en el mismo hilo (logs secuenciales, fácil depurar)
 * prod → ThreadPoolTaskExecutor: handlers corren en pool dedicado (no bloquean el reactor)
 */
@Configuration
@EnableAsync
public class WhatsAppEventConfig {

    @Bean("whatsappEventExecutor")
    @Profile("prod")
    public Executor whatsappEventExecutorAsync() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("wa-event-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean("whatsappEventExecutor")
    @Profile("!prod")
    public Executor whatsappEventExecutorSync() {
        return new SyncTaskExecutor();
    }
}
