package com.vertx.template.middleware.ratelimit.impl;

import com.vertx.template.middleware.ratelimit.annotation.RateLimit;
import com.vertx.template.middleware.ratelimit.annotation.RateLimitType;
import com.vertx.template.middleware.ratelimit.core.RateLimitResult;
import com.vertx.template.middleware.ratelimit.core.RateLimiter;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import lombok.extern.slf4j.Slf4j;

/**
 * 滑动窗口限流器实现 使用时间戳队列记录请求时间，实现精确的滑动窗口限流
 *
 * @author System
 * @since 1.0.0
 */
@Slf4j
public class SlidingWindowRateLimiter implements RateLimiter {

  /** 存储每个key的请求时间戳队列 */
  private final ConcurrentHashMap<String, WindowData> windows = new ConcurrentHashMap<>();

  /** 存储每个key的锁，避免并发问题 */
  private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

  @Override
  public RateLimitResult tryAcquire(String key, RateLimit rateLimit) {
    long windowSizeMs = rateLimit.timeUnit().toMillis(rateLimit.window());
    long limit = rateLimit.limit();
    long currentTime = System.currentTimeMillis();

    // 获取或创建锁
    ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());

    lock.lock();
    try {
      // 获取或创建窗口数据
      WindowData windowData = windows.computeIfAbsent(key, k -> new WindowData());

      // 清理过期的请求记录
      long windowStart = currentTime - windowSizeMs;
      while (!windowData.timestamps.isEmpty() && windowData.timestamps.peek() <= windowStart) {
        windowData.timestamps.poll();
      }

      // 检查当前请求数是否超过限制
      if (windowData.timestamps.size() >= limit) {
        // 计算重试时间
        long oldestRequest = windowData.timestamps.peek();
        long retryAfterMs = oldestRequest + windowSizeMs - currentTime;
        long retryAfterSeconds = Math.max(1, (retryAfterMs + 999) / 1000); // 向上取整

        String limitInfo =
            String.format(
                "%d requests per %d %s",
                limit, rateLimit.window(), rateLimit.timeUnit().name().toLowerCase());

        return RateLimitResult.rejected(
            limit, oldestRequest + windowSizeMs, retryAfterSeconds, key, limitInfo);
      }

      // 记录当前请求
      windowData.timestamps.offer(currentTime);
      windowData.lastAccessTime.set(currentTime);

      long remaining = limit - windowData.timestamps.size();
      long resetTime = currentTime + windowSizeMs;

      String limitInfo =
          String.format(
              "%d requests per %d %s",
              limit, rateLimit.window(), rateLimit.timeUnit().name().toLowerCase());

      return RateLimitResult.allowed(remaining, limit, resetTime, key, limitInfo);

    } finally {
      lock.unlock();
    }
  }

  @Override
  public String getType() {
    return RateLimitType.SLIDING_WINDOW.name();
  }

  @Override
  public void cleanup() {
    long currentTime = System.currentTimeMillis();
    long expireTime = currentTime - 3600000; // 1小时过期

    windows
        .entrySet()
        .removeIf(
            entry -> {
              String key = entry.getKey();
              WindowData windowData = entry.getValue();

              // 如果窗口数据超过1小时未访问，则清理
              if (windowData.lastAccessTime.get() < expireTime) {
                locks.remove(key);
                log.debug("Cleaned up expired rate limit window for key: {}", key);
                return true;
              }
              return false;
            });

    log.debug("Rate limiter cleanup completed. Active windows: {}", windows.size());
  }

  /** 窗口数据 */
  private static class WindowData {
    /** 请求时间戳队列 */
    final Queue<Long> timestamps = new LinkedList<>();

    /** 最后访问时间 */
    final AtomicLong lastAccessTime = new AtomicLong(System.currentTimeMillis());
  }
}
