package com.vertx.template.exception;

import lombok.Getter;

/** 限流异常 当请求超过限流阈值时抛出此异常 */
@Getter
public class RateLimitException extends BusinessException {

  /** 限流键 */
  private final String rateLimitKey;

  /** 限流配置信息 */
  private final String limitInfo;

  /** 重试建议时间（秒） */
  private final long retryAfterSeconds;

  public RateLimitException(String rateLimitKey, String limitInfo, long retryAfterSeconds) {
    super(429, String.format("请求过于频繁，请稍后重试。限流规则: %s", limitInfo));
    this.rateLimitKey = rateLimitKey;
    this.limitInfo = limitInfo;
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public RateLimitException(
      String message, String rateLimitKey, String limitInfo, long retryAfterSeconds) {
    super(429, message);
    this.rateLimitKey = rateLimitKey;
    this.limitInfo = limitInfo;
    this.retryAfterSeconds = retryAfterSeconds;
  }
}
