package com.vertx.template.middleware.ratelimit.core;

import com.vertx.template.middleware.ratelimit.annotation.RateLimit;

/**
 * 限流器接口 定义限流的核心方法
 *
 * @author System
 * @since 1.0.0
 */
public interface RateLimiter {

  /**
   * 尝试获取许可
   *
   * @param key 限流键
   * @param rateLimit 限流配置
   * @return 限流结果
   */
  RateLimitResult tryAcquire(String key, RateLimit rateLimit);

  /**
   * 获取限流器类型
   *
   * @return 限流器支持的算法类型
   */
  String getType();

  /** 清理过期的限流记录 定期调用以释放内存 */
  void cleanup();
}
