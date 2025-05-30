package com.vertx.template.middleware.ratelimit.annotation;

/**
 * 限流算法类型
 *
 * @author System
 * @since 1.0.0
 */
public enum RateLimitType {

  /** 固定窗口算法 在固定时间窗口内限制请求数量 */
  FIXED_WINDOW,

  /** 滑动窗口算法 使用滑动时间窗口，更平滑的限流效果 */
  SLIDING_WINDOW,

  /** 令牌桶算法 以固定速率生成令牌，请求消耗令牌 */
  TOKEN_BUCKET,

  /** 漏桶算法 以固定速率处理请求，超出部分被丢弃 */
  LEAKY_BUCKET
}
