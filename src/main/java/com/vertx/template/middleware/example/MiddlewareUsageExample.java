package com.vertx.template.middleware.example;

import com.vertx.template.middleware.common.MiddlewareManager;
import com.vertx.template.middleware.common.MiddlewareResult;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 中间件使用示例 展示如何在Vert.x应用中集成新的中间件系统
 *
 * @author 系统
 * @since 1.0.0
 */
public class MiddlewareUsageExample extends AbstractVerticle {

  private static final Logger logger = LoggerFactory.getLogger(MiddlewareUsageExample.class);

  private MiddlewareManager middlewareManager;

  @Override
  public void start(Promise<Void> startPromise) {
    // 初始化中间件管理器
    middlewareManager = new MiddlewareManager();

    // 注册中间件
    middlewareManager.register(new AuthMiddleware());
    middlewareManager.register(new LoggingMiddleware());
    middlewareManager.register(new RateLimitMiddleware());

    // 创建路由
    Router router = Router.router(vertx);

    // 应用中间件到所有路由
    router.route().handler(this::applyMiddlewares);

    // 定义业务路由
    router.get("/api/users").handler(this::getUsersHandler);
    router.post("/api/users").handler(this::createUserHandler);

    // 启动HTTP服务器
    vertx
        .createHttpServer()
        .requestHandler(router)
        .listen(8080)
        .onSuccess(
            server -> {
              logger.info("HTTP服务器启动成功，端口: {}", server.actualPort());
              startPromise.complete();
            })
        .onFailure(startPromise::fail);
  }

  /**
   * 应用中间件到请求
   *
   * @param context 路由上下文
   */
  private void applyMiddlewares(RoutingContext context) {
    middlewareManager
        .execute(context)
        .onSuccess(
            result -> {
              if (result.isSuccess() && result.shouldContinueChain()) {
                // 中间件执行成功，继续处理请求
                context.next();
              } else {
                // 中间件执行失败或要求停止，返回响应
                handleMiddlewareResult(context, result);
              }
            })
        .onFailure(
            throwable -> {
              // 中间件执行异常
              logger.error("中间件执行异常", throwable);
              context
                  .response()
                  .setStatusCode(500)
                  .putHeader("Content-Type", "application/json")
                  .end(
                      new JsonObject()
                          .put("success", false)
                          .put("code", "500")
                          .put("message", "服务器内部错误")
                          .put("data", throwable.getMessage())
                          .encode());
            });
  }

  /**
   * 处理中间件执行结果
   *
   * @param context 路由上下文
   * @param result 中间件执行结果
   */
  private void handleMiddlewareResult(RoutingContext context, MiddlewareResult result) {
    int statusCode = parseStatusCode(result.getStatusCode());

    JsonObject response =
        new JsonObject()
            .put("success", result.isSuccess())
            .put("code", result.getStatusCode())
            .put("message", result.getMessage());

    if (result.getData() != null) {
      response.put("data", result.getData());
    }

    context
        .response()
        .setStatusCode(statusCode)
        .putHeader("Content-Type", "application/json")
        .end(response.encode());
  }

  /**
   * 解析状态码
   *
   * @param statusCode 状态码字符串
   * @return HTTP状态码
   */
  private int parseStatusCode(String statusCode) {
    try {
      return Integer.parseInt(statusCode);
    } catch (NumberFormatException e) {
      logger.warn("无效的状态码: {}, 使用默认值500", statusCode);
      return 500;
    }
  }

  /**
   * 获取用户列表处理器
   *
   * @param context 路由上下文
   */
  private void getUsersHandler(RoutingContext context) {
    // 从中间件中获取用户信息
    String userId = context.get("userId");
    String userRole = context.get("userRole");

    JsonObject response =
        new JsonObject()
            .put("success", true)
            .put("code", "200")
            .put("message", "获取用户列表成功")
            .put(
                "data",
                new JsonObject()
                    .put("currentUser", userId)
                    .put("currentRole", userRole)
                    .put("users", new JsonObject().put("total", 100).put("list", "用户列表数据...")));

    context.response().putHeader("Content-Type", "application/json").end(response.encode());
  }

  /**
   * 创建用户处理器
   *
   * @param context 路由上下文
   */
  private void createUserHandler(RoutingContext context) {
    JsonObject response =
        new JsonObject()
            .put("success", true)
            .put("code", "201")
            .put("message", "用户创建成功")
            .put(
                "data",
                new JsonObject()
                    .put("userId", "new_user_123")
                    .put("createdAt", System.currentTimeMillis()));

    context
        .response()
        .setStatusCode(201)
        .putHeader("Content-Type", "application/json")
        .end(response.encode());
  }
}
