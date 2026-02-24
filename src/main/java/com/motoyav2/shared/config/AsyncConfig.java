package com.motoyav2.shared.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * Configura los thread pools dedicados para las integraciones externas bloqueantes.
 *
 * <p>Uso con anotación (en servicios no reactivos):
 * <pre>
 *   {@code @Async("twilioExecutor")}
 *   public CompletableFuture<Void> sendWhatsApp(...) { ... }
 * </pre>
 *
 * <p>Uso en cadenas reactivas (WebFlux) para envolver SDKs bloqueantes:
 * <pre>
 *   Mono.fromCallable(() -> twilioClient.sendMessage(...))
 *       .subscribeOn(twilioScheduler)
 *       .timeout(Duration.ofSeconds(10))
 * </pre>
 */
@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    // -------------------------------------------------------------------------
    // Twilio — WA/SMS: hasta 10 hilos, falla rápido si la cola se llena
    // -------------------------------------------------------------------------

    @Bean("twilioExecutor")
    public ThreadPoolTaskExecutor twilioExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(10);
        exec.setQueueCapacity(50);
        exec.setKeepAliveSeconds(30);
        exec.setThreadNamePrefix("twilio-");
        // CallerRunsPolicy: si la cola está llena el hilo del caller ejecuta la tarea
        // (evita perder mensajes a costa de bloquear brevemente al llamador)
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        exec.initialize();
        return exec;
    }

    @Bean("twilioScheduler")
    public Scheduler twilioScheduler() {
        return Schedulers.fromExecutor(twilioExecutor());
    }

    // -------------------------------------------------------------------------
    // SUNAT OSE — firma XML + CDR polling: pocos hilos pero operaciones largas
    // -------------------------------------------------------------------------

    @Bean("sunatExecutor")
    public ThreadPoolTaskExecutor sunatExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(2);
        exec.setMaxPoolSize(5);
        exec.setQueueCapacity(20);
        exec.setKeepAliveSeconds(60);
        exec.setThreadNamePrefix("sunat-");
        // AbortPolicy: rechaza con excepción; Resilience4j Bulkhead ya limitó el acceso
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        exec.initialize();
        return exec;
    }

    @Bean("sunatScheduler")
    public Scheduler sunatScheduler() {
        return Schedulers.fromExecutor(sunatExecutor());
    }

    // -------------------------------------------------------------------------
    // Document AI — OCR/extracción: costoso, pocos concurrentes
    // -------------------------------------------------------------------------

    @Bean("documentAiExecutor")
    public ThreadPoolTaskExecutor documentAiExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(1);
        exec.setMaxPoolSize(3);
        exec.setQueueCapacity(10);
        exec.setKeepAliveSeconds(60);
        exec.setThreadNamePrefix("document-ai-");
        exec.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        exec.initialize();
        return exec;
    }

    @Bean("documentAiScheduler")
    public Scheduler documentAiScheduler() {
        return Schedulers.fromExecutor(documentAiExecutor());
    }

    // -------------------------------------------------------------------------
    // Executor por defecto para @Async sin nombre explícito
    // -------------------------------------------------------------------------

    @Override
    public Executor getAsyncExecutor() {
        return twilioExecutor();
    }

    // -------------------------------------------------------------------------
    // Manejo de excepciones no capturadas en métodos @Async void
    // -------------------------------------------------------------------------

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new LoggingAsyncUncaughtExceptionHandler();
    }

    private static class LoggingAsyncUncaughtExceptionHandler
            implements AsyncUncaughtExceptionHandler {

        @Override
        public void handleUncaughtException(Throwable ex, Method method, Object... params) {
            log.error("[Async] Excepción no capturada en {}.{}(): {}",
                    method.getDeclaringClass().getSimpleName(),
                    method.getName(),
                    ex.getMessage(),
                    ex);
        }
    }
}