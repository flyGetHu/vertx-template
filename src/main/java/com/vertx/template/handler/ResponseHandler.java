package com.vertx.template.handler;

import javax.inject.Inject;
import javax.inject.Singleton;
import com.vertx.template.exception.BusinessException;
import com.vertx.template.exception.ValidationException;
import com.vertx.template.model.ApiResponse;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;

import java.util.function.Function;

/**
 * 统一响应处理器，简化控制器代码
 */
@Singleton
public class ResponseHandler {

  @Inject
  public ResponseHandler() {}

  /**
   * 创建一个Handler，自动处理响应和异常
   *
   * @param handler
   *          业务处理函数，直接返回业务数据对象
   * @param <T>
   *          返回数据类型
   * @return Vert.x Handler
   */
  public <T> Handler<RoutingContext> handle(Function<RoutingContext, T> handler) {
    return ctx -> {
      try {
        // 执行业务逻辑并获取结果
        T result = handler.apply(ctx);

        // 如果结果为null，则返回空对象
        if (result == null) {
          sendResponse(ctx, ApiResponse.success(null));
          return;
        }

        // 如果结果已经是ApiResponse，则直接返回
        if (result instanceof ApiResponse) {
          sendResponse(ctx, result);
          return;
        }

        // 将业务数据包装成ApiResponse
        sendResponse(ctx, ApiResponse.success(result));
      } catch (Exception e) {
        handleException(ctx, e);
      }
    };
  }

  /**
   * 发送响应
   */
  private void sendResponse(RoutingContext ctx, Object response) {
    ctx.response().putHeader("content-type", "application/json").setStatusCode(200).end(Json.encodePrettily(response));
  }

  /**
   * 处理异常
   */
  private void handleException(RoutingContext ctx, Throwable e) {
    if (e instanceof ValidationException) {
      ValidationException ex = (ValidationException) e;
      ApiResponse<?> response = ApiResponse.error(ex.getCode(), ex.getMessage());
      // 添加验证错误信息
      response.setExtra("validationErrors", ex.getValidationErrors());
      sendResponse(ctx, response);
    } else if (e instanceof BusinessException) {
      BusinessException ex = (BusinessException) e;
      sendResponse(ctx, ApiResponse.error(ex.getCode(), ex.getMessage()));
    } else {
      String errorMessage = e.getMessage() != null ? e.getMessage() : "Internal Server Error";
      sendResponse(ctx, ApiResponse.error(500, errorMessage));
    }
  }
}
