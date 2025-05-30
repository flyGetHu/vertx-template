package com.vertx.template.middleware.ratelimit.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 限流结果 封装限流检查的结果信息
 *
 * @author System
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitResult {

  /** 是否允许通过 */
  private boolean allowed;

  /** 剩余配额 */
  private long remaining;

  /** 总配额 */
  private long total;

  /** 重置时间（毫秒时间戳） */
  private long resetTime;

  /** 重试建议等待时间（秒） */
  private long retryAfterSeconds;

  /** 限流键 */
  private String rateLimitKey;

  /** 限流配置信息 */
  private String limitInfo;

  /** 创建允许通过的结果 */
  public static RateLimitResult allowed(
      long remaining, long total, long resetTime, String rateLimitKey, String limitInfo) {
    RateLimitResult result = new RateLimitResult();
    result.setAllowed(true);
    result.setRemaining(remaining);
    result.setTotal(total);
    result.setResetTime(resetTime);
    result.setRateLimitKey(rateLimitKey);
    result.setLimitInfo(limitInfo);
    result.setRetryAfterSeconds(0);
    return result;
  }

  /** 创建拒绝通过的结果 */
  public static RateLimitResult rejected(
      long total, long resetTime, long retryAfterSeconds, String rateLimitKey, String limitInfo) {
    RateLimitResult result = new RateLimitResult();
    result.setAllowed(false);
    result.setRemaining(0);
    result.setTotal(total);
    result.setResetTime(resetTime);
    result.setRetryAfterSeconds(retryAfterSeconds);
    result.setRateLimitKey(rateLimitKey);
    result.setLimitInfo(limitInfo);
    return result;
  }
}
