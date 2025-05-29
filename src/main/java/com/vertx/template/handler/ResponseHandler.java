package com.vertx.template.handler;

import com.vertx.template.model.dto.ApiResponse;
import io.vertx.core.Handler;
import io.vertx.core.json.Json;
import io.vertx.ext.web.RoutingContext;
import java.util.function.Function;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 统一响应处理器，简化控制器代码 */
@Singleton
public class ResponseHandler {

  private static final Logger logger = LoggerFactory.getLogger(ResponseHandler.class);

  /**
   * 创建一个Handler，自动处理响应和异常
   *
   * @param handler 业务处理函数，直接返回业务数据对象
   * @param <T> 返回数据类型
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
        logger.error("处理请求失败", e);
        ctx.fail(e);
      }
    };
  }

  /** 发送响应 */
  private void sendResponse(RoutingContext ctx, Object response) {
    try {
      ctx.response()
          .putHeader("content-type", "application/json")
          .setStatusCode(200)
          .end(Json.encodePrettily(response));
    } catch (Exception e) {
      // 序列化异常交给全局异常处理器处理
      ctx.fail(e);
    }
  }
}
