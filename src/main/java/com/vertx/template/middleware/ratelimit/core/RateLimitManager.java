package com.vertx.template.middleware.ratelimit.core;

import com.google.inject.Singleton;
import com.vertx.template.middleware.ratelimit.annotation.RateLimit;
import com.vertx.template.middleware.ratelimit.annotation.RateLimitType;
import com.vertx.template.middleware.ratelimit.impl.FixedWindowRateLimiter;
import com.vertx.template.middleware.ratelimit.impl.SlidingWindowRateLimiter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * 限流管理器 统一管理不同类型的限流器，提供限流检查和清理功能
 *
 * @author System
 * @since 1.0.0
 */
@Slf4j
@Singleton
public class RateLimitManager {

  /** 限流器映射 */
  private final Map<RateLimitType, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

  /** 清理任务调度器 */
  private final ScheduledExecutorService cleanupScheduler;

  /** 是否已初始化 */
  private volatile boolean initialized = false;

  public RateLimitManager() {
    this.cleanupScheduler =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              Thread thread = new Thread(r, "rate-limit-cleanup");
              thread.setDaemon(true);
              return thread;
            });

    initialize();
  }

  /** 初始化限流器 */
  private void initialize() {
    if (initialized) {
      return;
    }

    synchronized (this) {
      if (initialized) {
        return;
      }

      try {
        // 注册内置限流器
        registerRateLimiter(RateLimitType.SLIDING_WINDOW, new SlidingWindowRateLimiter());
        registerRateLimiter(RateLimitType.FIXED_WINDOW, new FixedWindowRateLimiter());

        // 启动清理任务，每5分钟执行一次
        cleanupScheduler.scheduleWithFixedDelay(this::cleanup, 5, 5, TimeUnit.MINUTES);

        initialized = true;
        log.info("Rate limit manager initialized with {} rate limiters", rateLimiters.size());

      } catch (Exception e) {
        log.error("Failed to initialize rate limit manager", e);
        throw new RuntimeException("Rate limit manager initialization failed", e);
      }
    }
  }

  /** 注册限流器 */
  public void registerRateLimiter(RateLimitType type, RateLimiter rateLimiter) {
    rateLimiters.put(type, rateLimiter);
    log.debug("Registered rate limiter for type: {}", type);
  }

  /** 检查限流 */
  public RateLimitResult checkRateLimit(String key, RateLimit rateLimit) {
    if (!rateLimit.enabled()) {
      // 如果限流未启用，直接允许通过
      String limitInfo =
          String.format(
              "%d requests per %d %s (disabled)",
              rateLimit.limit(), rateLimit.window(), rateLimit.timeUnit().name().toLowerCase());
      return RateLimitResult.allowed(
          rateLimit.limit(),
          rateLimit.limit(),
          System.currentTimeMillis() + rateLimit.timeUnit().toMillis(rateLimit.window()),
          key,
          limitInfo);
    }

    RateLimiter rateLimiter = rateLimiters.get(rateLimit.type());
    if (rateLimiter == null) {
      log.warn(
          "No rate limiter found for type: {}, using default SLIDING_WINDOW", rateLimit.type());
      rateLimiter = rateLimiters.get(RateLimitType.SLIDING_WINDOW);
    }

    if (rateLimiter == null) {
      log.error("No rate limiter available, allowing request");
      String limitInfo = "No rate limiter available";
      return RateLimitResult.allowed(
          rateLimit.limit(),
          rateLimit.limit(),
          System.currentTimeMillis() + rateLimit.timeUnit().toMillis(rateLimit.window()),
          key,
          limitInfo);
    }

    try {
      RateLimitResult result = rateLimiter.tryAcquire(key, rateLimit);

      if (log.isDebugEnabled()) {
        log.debug(
            "Rate limit check for key '{}': allowed={}, remaining={}, total={}",
            key,
            result.isAllowed(),
            result.getRemaining(),
            result.getTotal());
      }

      return result;

    } catch (Exception e) {
      log.error("Error during rate limit check for key: {}", key, e);
      // 发生错误时，为了系统稳定性，允许请求通过
      String limitInfo = "Rate limit check error";
      return RateLimitResult.allowed(
          rateLimit.limit(),
          rateLimit.limit(),
          System.currentTimeMillis() + rateLimit.timeUnit().toMillis(rateLimit.window()),
          key,
          limitInfo);
    }
  }

  /** 清理过期数据 */
  public void cleanup() {
    try {
      log.debug("Starting rate limiter cleanup");

      for (Map.Entry<RateLimitType, RateLimiter> entry : rateLimiters.entrySet()) {
        try {
          entry.getValue().cleanup();
        } catch (Exception e) {
          log.warn("Error during cleanup for rate limiter type: {}", entry.getKey(), e);
        }
      }

      log.debug("Rate limiter cleanup completed");

    } catch (Exception e) {
      log.error("Error during rate limiter cleanup", e);
    }
  }

  /** 获取限流器统计信息 */
  public Map<String, Object> getStatistics() {
    Map<String, Object> stats = new ConcurrentHashMap<>();
    stats.put("initialized", initialized);
    stats.put("rateLimiterCount", rateLimiters.size());
    stats.put("availableTypes", rateLimiters.keySet());
    return stats;
  }

  /** 关闭管理器 */
  public void shutdown() {
    try {
      log.info("Shutting down rate limit manager");

      // 执行最后一次清理
      cleanup();

      // 关闭调度器
      cleanupScheduler.shutdown();
      if (!cleanupScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
        cleanupScheduler.shutdownNow();
      }

      initialized = false;
      log.info("Rate limit manager shutdown completed");

    } catch (Exception e) {
      log.error("Error during rate limit manager shutdown", e);
    }
  }
}
