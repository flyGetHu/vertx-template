package com.vertx.template.middleware.ratelimit.impl;

import com.vertx.template.middleware.ratelimit.annotation.RateLimit;
import com.vertx.template.middleware.ratelimit.annotation.RateLimitType;
import com.vertx.template.middleware.ratelimit.core.RateLimitResult;
import com.vertx.template.middleware.ratelimit.core.RateLimiter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;

/**
 * 固定窗口限流器实现 在固定时间窗口内限制请求数量，实现简单，性能较好
 *
 * @author System
 * @since 1.0.0
 */
@Slf4j
public class FixedWindowRateLimiter implements RateLimiter {

  /** 存储每个key的窗口数据 */
  private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

  /** 存储每个key的锁，避免并发问题 */
  private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

  @Override
  public RateLimitResult tryAcquire(String key, RateLimit rateLimit) {
    long windowSizeMs = rateLimit.timeUnit().toMillis(rateLimit.window());
    long limit = rateLimit.limit();
    long currentTime = System.currentTimeMillis();

    // 计算当前窗口的开始时间
    long windowStart = (currentTime / windowSizeMs) * windowSizeMs;
    long windowEnd = windowStart + windowSizeMs;

    // 获取或创建锁
    ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());

    lock.lock();
    try {
      // 获取或创建计数器
      WindowCounter counter = counters.computeIfAbsent(key, k -> new WindowCounter());

      // 如果是新的时间窗口，重置计数器
      if (counter.windowStart.get() != windowStart) {
        counter.windowStart.set(windowStart);
        counter.count.set(0);
      }

      // 检查当前请求数是否超过限制
      long currentCount = counter.count.get();
      if (currentCount >= limit) {
        // 计算重试时间
        long retryAfterMs = windowEnd - currentTime;
        long retryAfterSeconds = Math.max(1, (retryAfterMs + 999) / 1000); // 向上取整

        String limitInfo =
            String.format(
                "%d requests per %d %s (fixed window)",
                limit, rateLimit.window(), rateLimit.timeUnit().name().toLowerCase());

        return RateLimitResult.rejected(limit, windowEnd, retryAfterSeconds, key, limitInfo);
      }

      // 增加计数器
      counter.count.incrementAndGet();
      counter.lastAccessTime.set(currentTime);

      long remaining = limit - counter.count.get();

      String limitInfo =
          String.format(
              "%d requests per %d %s (fixed window)",
              limit, rateLimit.window(), rateLimit.timeUnit().name().toLowerCase());

      return RateLimitResult.allowed(remaining, limit, windowEnd, key, limitInfo);

    } finally {
      lock.unlock();
    }
  }

  @Override
  public String getType() {
    return RateLimitType.FIXED_WINDOW.name();
  }

  @Override
  public void cleanup() {
    long currentTime = System.currentTimeMillis();
    long expireTime = currentTime - 3600000; // 1小时过期

    counters
        .entrySet()
        .removeIf(
            entry -> {
              String key = entry.getKey();
              WindowCounter counter = entry.getValue();

              // 如果计数器超过1小时未访问，则清理
              if (counter.lastAccessTime.get() < expireTime) {
                locks.remove(key);
                log.debug("Cleaned up expired rate limit counter for key: {}", key);
                return true;
              }
              return false;
            });

    log.debug("Rate limiter cleanup completed. Active counters: {}", counters.size());
  }

  /** 窗口计数器 */
  private static class WindowCounter {
    /** 窗口开始时间 */
    final AtomicLong windowStart = new AtomicLong(0);

    /** 当前窗口请求计数 */
    final AtomicLong count = new AtomicLong(0);

    /** 最后访问时间 */
    final AtomicLong lastAccessTime = new AtomicLong(System.currentTimeMillis());
  }
}
