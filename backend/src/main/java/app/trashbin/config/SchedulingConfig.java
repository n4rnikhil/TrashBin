package app.trashbin.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables background jobs: token cleanup, storage reconciliation (and retention in M5). */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
public class SchedulingConfig {}
