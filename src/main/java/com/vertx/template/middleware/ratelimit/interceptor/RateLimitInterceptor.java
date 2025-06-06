package com.vertx.template.middleware.ratelimit.interceptor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.vertx.template.exception.RateLimitException;
import com.vertx.template.middleware.ratelimit.annotation.RateLimit;
import com.vertx.template.middleware.ratelimit.core.RateLimitKeyGenerator;
import com.vertx.template.middleware.ratelimit.core.RateLimitManager;
import com.vertx.template.middleware.ratelimit.core.RateLimitResult;
import io.vertx.ext.web.RoutingContext;
import java.lang.reflect.Method;
import lombok.extern.slf4j.Slf4j;

/**
 * 限流拦截器 负责拦截带有@RateLimit注解的方法，执行限流检查
 *
 * @author System
 * @since 1.0.0
 */
@Slf4j
@Singleton
public class RateLimitInterceptor {

  private final RateLimitManager rateLimitManager;

  @Inject
  public RateLimitInterceptor(RateLimitManager rateLimitManager) {
    this.rateLimitManager = rateLimitManager;
  }

  /**
   * 执行限流检查
   *
   * @param rateLimit 限流注解
   * @param method 目标方法
   * @param context Vert.x路由上下文
   * @param args 方法参数
   * @throws RateLimitException 当超过限流阈值时抛出
   */
  public void checkRateLimit(
      RateLimit rateLimit, Method method, RoutingContext context, Object[] args) {
    try {
      // 生成限流键
      String rateLimitKey = RateLimitKeyGenerator.generateKey(rateLimit, method, context, args);

      // 执行限流检查
      RateLimitResult result = rateLimitManager.checkRateLimit(rateLimitKey, rateLimit);

      // 设置响应头
      setRateLimitHeaders(context, result);

      // 如果被限流，抛出异常
      if (!result.isAllowed()) {
        log.warn(
            "Rate limit exceeded for key: {}, limit info: {}", rateLimitKey, result.getLimitInfo());

        throw new RateLimitException(
            rateLimitKey, result.getLimitInfo(), result.getRetryAfterSeconds());
      }

      if (log.isDebugEnabled()) {
        log.debug(
            "Rate limit check passed for key: {}, remaining: {}/{}",
            rateLimitKey,
            result.getRemaining(),
            result.getTotal());
      }

    } catch (RateLimitException e) {
      // 重新抛出限流异常
      throw e;
    } catch (Exception e) {
      log.error("Error during rate limit check", e);
      // 为了系统稳定性，发生错误时允许请求通过
      // 但记录错误日志以便排查问题
    }
  }

  /** 设置限流相关的响应头 */
  private void setRateLimitHeaders(RoutingContext context, RateLimitResult result) {
    if (context == null || context.response() == null) {
      return;
    }

    try {
      // 设置标准的限流响应头
      context.response().putHeader("X-RateLimit-Limit", String.valueOf(result.getTotal()));
      context.response().putHeader("X-RateLimit-Remaining", String.valueOf(result.getRemaining()));
      context
          .response()
          .putHeader("X-RateLimit-Reset", String.valueOf(result.getResetTime() / 1000));

      // 如果被限流，设置重试时间
      if (!result.isAllowed() && result.getRetryAfterSeconds() > 0) {
        context.response().putHeader("Retry-After", String.valueOf(result.getRetryAfterSeconds()));
      }

    } catch (Exception e) {
      log.warn("Failed to set rate limit headers", e);
    }
  }

  /** 检查类级别的限流注解 */
  public void checkClassLevelRateLimit(
      Class<?> clazz, Method method, RoutingContext context, Object[] args) {
    RateLimit classRateLimit = clazz.getAnnotation(RateLimit.class);
    if (classRateLimit != null) {
      checkRateLimit(classRateLimit, method, context, args);
    }
  }

  /** 检查方法级别的限流注解 */
  public void checkMethodLevelRateLimit(Method method, RoutingContext context, Object[] args) {
    RateLimit methodRateLimit = method.getAnnotation(RateLimit.class);
    if (methodRateLimit != null) {
      checkRateLimit(methodRateLimit, method, context, args);
    }
  }

  /** 执行完整的限流检查（类级别 + 方法级别） */
  public void performRateLimitCheck(
      Class<?> clazz, Method method, RoutingContext context, Object[] args) {
    // 先检查类级别的限流
    checkClassLevelRateLimit(clazz, method, context, args);

    // 再检查方法级别的限流
    checkMethodLevelRateLimit(method, context, args);
  }
}
