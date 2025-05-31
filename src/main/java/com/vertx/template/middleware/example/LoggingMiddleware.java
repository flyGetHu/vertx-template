package com.vertx.template.middleware.example;

import com.vertx.template.middleware.common.Middleware;
import com.vertx.template.middleware.common.MiddlewareResult;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 日志中间件示例 记录请求的基本信息
 *
 * @author 系统
 * @since 1.0.0
 */
public class LoggingMiddleware implements Middleware {

  private static final Logger logger = LoggerFactory.getLogger(LoggingMiddleware.class);

  @Override
  public Future<MiddlewareResult> handle(RoutingContext context) {
    try {
      String method = context.request().method().name();
      String path = context.request().path();
      String clientIP = context.request().remoteAddress().host();
      String userAgent = context.request().getHeader("User-Agent");

      logger.info(
          "请求日志 - 方法: {}, 路径: {}, 客户端IP: {}, User-Agent: {}", method, path, clientIP, userAgent);

      // 记录请求开始时间，用于计算处理时间
      context.put("requestStartTime", System.currentTimeMillis());

      return Future.succeededFuture(MiddlewareResult.success("日志记录完成"));

    } catch (Exception e) {
      logger.error("日志中间件执行异常", e);
      return Future.succeededFuture(MiddlewareResult.failure("500", "日志记录失败: " + e.getMessage()));
    }
  }

  @Override
  public String getName() {
    return "LoggingMiddleware";
  }

  @Override
  public int getOrder() {
    return 5; // 日志中间件优先级最高
  }
}
