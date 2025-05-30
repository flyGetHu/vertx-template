package com.vertx.template.controller;

import com.vertx.template.middleware.ratelimit.annotation.RateLimit;
import com.vertx.template.middleware.ratelimit.annotation.RateLimitDimension;
import com.vertx.template.middleware.ratelimit.annotation.RateLimitType;
import com.vertx.template.model.dto.ApiResponse;
import com.vertx.template.router.annotation.GetMapping;
import com.vertx.template.router.annotation.PostMapping;
import com.vertx.template.router.annotation.RequestMapping;
import com.vertx.template.router.annotation.RestController;
import io.vertx.ext.web.RoutingContext;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * 限流功能演示控制器 展示不同类型的限流配置和使用方式
 *
 * @author System
 * @since 1.0.0
 */
@Slf4j
@Singleton
@RestController
@RequestMapping("/api/ratelimit")
public class RateLimitDemoController {

  /** 基础限流示例 每分钟最多10次请求，按IP限流 */
  @GetMapping("/basic")
  @RateLimit(
      limit = 10,
      window = 1,
      timeUnit = TimeUnit.MINUTES,
      dimension = RateLimitDimension.IP,
      message = "基础接口访问过于频繁，请稍后重试")
  public ApiResponse<String> basicRateLimit() {
    log.info("基础限流接口被调用");
    return ApiResponse.success("基础限流接口调用成功，当前时间: " + LocalDateTime.now());
  }

  /** 严格限流示例 每30秒最多5次请求，按IP限流 */
  @GetMapping("/strict")
  @RateLimit(
      limit = 5,
      window = 30,
      timeUnit = TimeUnit.SECONDS,
      dimension = RateLimitDimension.IP,
      type = RateLimitType.SLIDING_WINDOW,
      message = "严格限流接口访问过于频繁，请等待30秒后重试")
  public ApiResponse<String> strictRateLimit() {
    log.info("严格限流接口被调用");
    return ApiResponse.success("严格限流接口调用成功，当前时间: " + LocalDateTime.now());
  }

  /** 全局限流示例 所有用户共享配额，每分钟最多100次请求 */
  @GetMapping("/global")
  @RateLimit(
      limit = 100,
      window = 1,
      timeUnit = TimeUnit.MINUTES,
      dimension = RateLimitDimension.GLOBAL,
      type = RateLimitType.FIXED_WINDOW,
      message = "全局接口访问量过大，请稍后重试")
  public ApiResponse<String> globalRateLimit() {
    log.info("全局限流接口被调用");
    return ApiResponse.success("全局限流接口调用成功，当前时间: " + LocalDateTime.now());
  }

  /** 用户级限流示例 每个用户每小时最多1000次请求 */
  @PostMapping("/user")
  @RateLimit(
      limit = 1000,
      window = 1,
      timeUnit = TimeUnit.HOURS,
      dimension = RateLimitDimension.USER,
      message = "用户接口访问过于频繁，请1小时后重试")
  public ApiResponse<String> userRateLimit(RoutingContext context) {
    log.info("用户级限流接口被调用");
    String userId = context.request().getHeader("X-User-Id");
    return ApiResponse.success("用户级限流接口调用成功，用户ID: " + userId + "，当前时间: " + LocalDateTime.now());
  }

  /** 自定义键限流示例 根据请求参数中的业务ID进行限流 */
  @GetMapping("/custom/:businessId")
  @RateLimit(
      limit = 20,
      window = 5,
      timeUnit = TimeUnit.MINUTES,
      dimension = RateLimitDimension.CUSTOM,
      customKey = "business_#arg0",
      message = "该业务ID访问过于频繁，请5分钟后重试")
  public ApiResponse<String> customRateLimit(String businessId) {
    log.info("自定义键限流接口被调用，业务ID: {}", businessId);
    return ApiResponse.success(
        "自定义键限流接口调用成功，业务ID: " + businessId + "，当前时间: " + LocalDateTime.now());
  }

  /** 无限流示例 演示如何禁用限流 */
  @GetMapping("/unlimited")
  @RateLimit(enabled = false, limit = 1, window = 1, timeUnit = TimeUnit.SECONDS)
  public ApiResponse<String> unlimitedAccess() {
    log.info("无限流接口被调用");
    return ApiResponse.success("无限流接口调用成功，当前时间: " + LocalDateTime.now());
  }

  /** 获取限流状态信息 */
  @GetMapping("/status")
  public ApiResponse<String> getRateLimitStatus() {
    return ApiResponse.success("限流功能正常运行，当前时间: " + LocalDateTime.now());
  }
}
