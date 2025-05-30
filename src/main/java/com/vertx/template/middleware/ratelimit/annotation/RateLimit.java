package com.vertx.template.middleware.ratelimit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 限流注解 用于标记需要进行限流的方法
 *
 * @author System
 * @since 1.0.0
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

  /** 限流键的前缀，用于区分不同的限流规则 默认使用方法签名 */
  String key() default "";

  /** 限流算法类型 */
  RateLimitType type() default RateLimitType.SLIDING_WINDOW;

  /** 时间窗口大小 */
  long window() default 60;

  /** 时间单位 */
  TimeUnit timeUnit() default TimeUnit.SECONDS;

  /** 允许的最大请求数 */
  long limit() default 100;

  /** 限流维度 IP: 按IP地址限流 USER: 按用户ID限流 GLOBAL: 全局限流 CUSTOM: 自定义限流键 */
  RateLimitDimension dimension() default RateLimitDimension.IP;

  /** 自定义限流键表达式（当dimension为CUSTOM时使用） 支持SpEL表达式，可以访问方法参数 例如: "#userId" 或 "#request.userId" */
  String customKey() default "";

  /** 限流失败时的提示信息 */
  String message() default "请求过于频繁，请稍后重试";

  /** 是否启用限流 可以通过配置文件动态控制 */
  boolean enabled() default true;
}
