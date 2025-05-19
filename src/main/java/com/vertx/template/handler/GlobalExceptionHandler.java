package com.vertx.template.handler;

import javax.inject.Inject;
import javax.inject.Singleton;
import com.vertx.template.exception.BusinessException;
import com.vertx.template.model.ApiResponse;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 全局异常处理器
 */
@Singleton
public class GlobalExceptionHandler implements Handler<RoutingContext> {
  private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @Inject
  public GlobalExceptionHandler() {}

  @Override
  public void handle(RoutingContext ctx) {
    Throwable failure = ctx.failure();
    if (failure != null) {
      // 记录异常日志
      logger.error("请求处理异常: " + ctx.request().path(), failure);

      // 构建错误响应
      ApiResponse<?> response;
      if (failure instanceof BusinessException) {
        BusinessException ex = (BusinessException) failure;
        response = ApiResponse.error(ex.getCode(), ex.getMessage());
      } else {
        response = ApiResponse.error(500,
            failure.getMessage() != null ? failure.getMessage() : "Internal Server Error");
      }

      // 发送响应
      ctx.response().setStatusCode(200) // 统一使用200状态码，通过业务code区分错误
          .putHeader("content-type", "application/json").end(Json.encodePrettily(response));
    } else {
      // 没有异常，继续处理
      ctx.next();
    }
  }
}
