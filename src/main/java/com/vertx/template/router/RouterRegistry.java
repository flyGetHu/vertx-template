package com.vertx.template.router;

// 日志
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.vertx.template.di.AppModule;
import com.vertx.template.handler.GlobalExceptionHandler;
import com.vertx.template.model.dto.ApiResponse;
import com.vertx.template.router.handler.AnnotationRouterHandler;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 路由注册中心，负责注册所有路由模块 */
public class RouterRegistry {
  private static final Logger logger = LoggerFactory.getLogger(RouterRegistry.class);

  // HTTP状态码常量
  private static final int HTTP_OK = 200;
  private static final int HTTP_NOT_FOUND = 404;
  private static final int HTTP_METHOD_NOT_ALLOWED = 405;
  private static final int HTTP_TOO_MANY_REQUESTS = 429;
  private static final int HTTP_SERVICE_UNAVAILABLE = 503;
  private static final int HTTP_GATEWAY_TIMEOUT = 504;

  // 响应头常量
  private static final String CONTENT_TYPE_HEADER = "content-type";
  private static final String APPLICATION_JSON = "application/json";

  // 包路径常量
  private static final String BASE_PACKAGE = "com.vertx.template";

  // 实例字段
  private final Vertx vertx;
  private final JsonObject config;
  private final Injector injector;
  private final Router mainRouter;

  /**
   * 构造函数
   *
   * @param vertx Vert.x实例
   * @param config 应用配置
   */
  public RouterRegistry(Vertx vertx, JsonObject config) {
    this.vertx = vertx;
    this.config = config;

    // 创建Guice注入器
    this.injector = Guice.createInjector(new AppModule(vertx, config));
    this.mainRouter = injector.getInstance(Router.class);
  }

  /**
   * 注册所有路由模块
   *
   * @return 配置好的Router实例
   */
  public Router registerAll() {
    // 注册全局中间件
    registerMiddlewares();

    // 注册基于注解的路由
    registerAnnotationRoutes();

    // 注册错误处理（应放在最后）
    registerExceptionHandler();

    return mainRouter;
  }

  /** 注册全局中间件 */
  private void registerMiddlewares() {
    // 通过注入器获取全局中间件
    GlobalMiddleware middleware = injector.getInstance(GlobalMiddleware.class);
    middleware.register();
  }

  /** 注册基于注解的路由 */
  private void registerAnnotationRoutes() {
    try {
      AnnotationRouterHandler annotationRouterHandler =
          injector.getInstance(AnnotationRouterHandler.class);
      annotationRouterHandler.registerRoutes(mainRouter);
      logger.info("基于注解的路由注册完成");
    } catch (Exception e) {
      logger.error("注册基于注解的路由失败", e);
    }
  }

  /** 注册全局异常处理器 */
  private void registerExceptionHandler() {
    // 注册HTTP状态码错误处理器
    registerHttpErrorHandlers();

    // 注册全局异常处理器（处理所有未捕获的异常）
    registerGlobalFailureHandler();

    logger.debug("全局异常处理器配置完成");
  }

  /** 注册HTTP状态码错误处理器 */
  private void registerHttpErrorHandlers() {
    registerErrorHandler(HTTP_NOT_FOUND, "Not Found");
    registerErrorHandler(HTTP_METHOD_NOT_ALLOWED, "Method Not Allowed");
    registerErrorHandler(HTTP_TOO_MANY_REQUESTS, "Too Many Requests");
    registerErrorHandler(HTTP_SERVICE_UNAVAILABLE, "Service Unavailable");
    registerErrorHandler(HTTP_GATEWAY_TIMEOUT, "Gateway Timeout");
  }

  /**
   * 注册单个错误处理器
   *
   * @param statusCode HTTP状态码
   * @param message 错误消息
   */
  private void registerErrorHandler(int statusCode, String message) {
    mainRouter.errorHandler(
        statusCode,
        ctx -> {
          ctx.response()
              .setStatusCode(HTTP_OK)
              .putHeader(CONTENT_TYPE_HEADER, APPLICATION_JSON)
              .end(Json.encodePrettily(ApiResponse.error(statusCode, message)));
        });
  }

  /** 注册全局失败处理器 */
  private void registerGlobalFailureHandler() {
    GlobalExceptionHandler globalExceptionHandler =
        injector.getInstance(GlobalExceptionHandler.class);
    mainRouter.route().failureHandler(globalExceptionHandler);
  }
}
