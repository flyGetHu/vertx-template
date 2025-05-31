package com.vertx.template.middleware.example;

import com.vertx.template.middleware.common.Middleware;
import com.vertx.template.middleware.common.MiddlewareResult;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 限流中间件示例 基于IP地址进行简单的请求频率限制
 *
 * @author 系统
 * @since 1.0.0
 */
public class RateLimitMiddleware implements Middleware {

  private static final Logger logger = LoggerFactory.getLogger(RateLimitMiddleware.class);

  /** 每分钟最大请求数 */
  private static final int MAX_REQUESTS_PER_MINUTE = 60;

  /** 存储每个IP的请求计数 */
  private final ConcurrentHashMap<String, RequestCounter> requestCounters =
      new ConcurrentHashMap<>();

  @Override
  public Future<MiddlewareResult> handle(RoutingContext context) {
    try {
      String clientIP = context.request().remoteAddress().host();

      // 获取或创建请求计数器
      RequestCounter counter = requestCounters.computeIfAbsent(clientIP, k -> new RequestCounter());

      // 检查是否超过限制
      if (counter.isExceeded()) {
        logger.warn("IP {} 请求频率超限，当前计数: {}", clientIP, counter.getCount());
        return Future.succeededFuture(MiddlewareResult.failure("429", "请求频率过高，请稍后再试"));
      }

      // 增加请求计数
      counter.increment();

      logger.debug("IP {} 请求计数: {}/{}", clientIP, counter.getCount(), MAX_REQUESTS_PER_MINUTE);

      return Future.succeededFuture(MiddlewareResult.success("限流检查通过"));

    } catch (Exception e) {
      logger.error("限流中间件执行异常", e);
      return Future.succeededFuture(MiddlewareResult.failure("500", "限流检查失败: " + e.getMessage()));
    }
  }

  @Override
  public String getName() {
    return "RateLimitMiddleware";
  }

  @Override
  public int getOrder() {
    return 20; // 限流中间件在认证之后执行
  }

  /** 请求计数器 */
  private static class RequestCounter {
    private final AtomicInteger count = new AtomicInteger(0);
    private volatile long windowStart = System.currentTimeMillis();

    /** 增加计数 */
    public void increment() {
      resetIfNeeded();
      count.incrementAndGet();
    }

    /** 获取当前计数 */
    public int getCount() {
      resetIfNeeded();
      return count.get();
    }

    /** 检查是否超过限制 */
    public boolean isExceeded() {
      resetIfNeeded();
      return count.get() >= MAX_REQUESTS_PER_MINUTE;
    }

    /** 如果需要，重置计数器（每分钟重置一次） */
    private void resetIfNeeded() {
      long now = System.currentTimeMillis();
      if (now - windowStart >= 60000) { // 60秒
        synchronized (this) {
          if (now - windowStart >= 60000) {
            count.set(0);
            windowStart = now;
          }
        }
      }
    }
  }
}
