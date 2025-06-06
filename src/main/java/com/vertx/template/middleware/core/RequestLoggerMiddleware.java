package com.vertx.template.middleware.core;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.vertx.template.middleware.common.Middleware;
import com.vertx.template.middleware.common.MiddlewareResult;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 请求日志中间件
 *
 * <p>记录HTTP请求的详细信息和处理时间
 *
 * @author 系统
 * @since 1.0.0
 */
@Singleton
public class RequestLoggerMiddleware implements Middleware {
  private static final Logger logger = LoggerFactory.getLogger(RequestLoggerMiddleware.class);
  private final boolean enabled;
  private final boolean logRequestDetails;
  private final boolean logResponseDetails;

  @Inject
  public RequestLoggerMiddleware(JsonObject config) {
    JsonObject logConfig = config.getJsonObject("request_log", new JsonObject());
    this.enabled = logConfig.getBoolean("enabled", true);
    this.logRequestDetails = logConfig.getBoolean("log_request_details", true);
    this.logResponseDetails = logConfig.getBoolean("log_response_details", true);

    if (enabled) {
      logger.debug("请求日志中间件初始化完成");
    } else {
      logger.info("请求日志中间件已禁用");
    }
  }

  @Override
  public MiddlewareResult handle(RoutingContext context) {
    if (!enabled) {
      return MiddlewareResult.success("请求日志中间件已禁用");
    }

    try {
      // 记录请求开始时间
      long startTime = System.currentTimeMillis();
      context.put("startTime", startTime);

      // 记录请求开始信息
      if (logRequestDetails && logger.isDebugEnabled()) {
        logger.debug(
            "Request started: {} {} from {} - User-Agent: {}",
            context.request().method(),
            context.request().path(),
            context.request().remoteAddress(),
            context.request().getHeader("User-Agent"));
      }

      // 添加响应结束处理器
      context.addHeadersEndHandler(
          v -> {
            if (logResponseDetails) {
              long endTime = System.currentTimeMillis();
              long duration = endTime - startTime;
              int statusCode = context.response().getStatusCode();

              // 根据状态码选择日志级别
              if (statusCode >= 500) {
                logger.error(
                    "Request completed: {} {} {} {}ms - ERROR",
                    context.request().method(),
                    context.request().path(),
                    statusCode,
                    duration);
              } else if (statusCode >= 400) {
                logger.warn(
                    "Request completed: {} {} {} {}ms - CLIENT_ERROR",
                    context.request().method(),
                    context.request().path(),
                    statusCode,
                    duration);
              } else {
                logger.info(
                    "Request completed: {} {} {} {}ms",
                    context.request().method(),
                    context.request().path(),
                    statusCode,
                    duration);
              }
            }
          });

      return MiddlewareResult.success("请求日志记录设置完成");
    } catch (Exception e) {
      logger.error("请求日志处理失败", e);
      return MiddlewareResult.failure("500", "请求日志处理失败: " + e.getMessage());
    }
  }

  @Override
  public String getName() {
    return "RequestLoggerMiddleware";
  }

  @Override
  public int getOrder() {
    return 30; // 在Body处理器之后执行
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }
}
