package com.vertx.template.middleware.ratelimit.annotation;

/**
 * 限流维度 定义限流的粒度和范围
 *
 * @author System
 * @since 1.0.0
 */
public enum RateLimitDimension {

  /** 按IP地址限流 每个IP地址独立计算限流 */
  IP,

  /** 按用户ID限流 每个登录用户独立计算限流 */
  USER,

  /** 全局限流 所有请求共享限流配额 */
  GLOBAL,

  /** 自定义限流键 使用自定义表达式生成限流键 */
  CUSTOM
}
