package dev.misteroumar.expenseflow.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Active {@code @Async}. Les tâches s'exécutent sur l'exécuteur {@code applicationTaskExecutor}
 * configuré par Spring Boot ({@code spring.task.execution.*}).
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
