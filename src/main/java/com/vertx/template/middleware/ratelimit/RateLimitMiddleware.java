package com.vertx.template.middleware.ratelimit;

import com.vertx.template.middleware.common.Middleware;
import io.vertx.ext.web.RoutingContext;

/**
 * 限流中间件接口 定义限流相关的中间件行为
 *
 * @author 系统
 * @since 1.0.0
 */
public interface RateLimitMiddleware extends Middleware {

  /**
   * 检查是否被限流
   *
   * @param context 路由上下文
   * @return 检查结果，true表示允许通过，false表示被限流
   */
  boolean checkRateLimit(RoutingContext context);

  /**
   * 获取剩余请求次数
   *
   * @param context 路由上下文
   * @return 剩余请求次数
   */
  long getRemainingRequests(RoutingContext context);

  /**
   * 获取重置时间（秒）
   *
   * @param context 路由上下文
   * @return 重置时间戳
   */
  long getResetTime(RoutingContext context);

  /**
   * 设置限流响应头
   *
   * @param context 路由上下文
   */
  void setRateLimitHeaders(RoutingContext context);

  @Override
  default int getOrder() {
    return 20; // 限流中间件在认证之后执行
  }
}
