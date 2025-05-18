package com.vertx.template.router;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.vertx.template.di.AppModule;
import com.vertx.template.exception.BusinessException;
import com.vertx.template.model.ApiResponse;
import com.vertx.template.router.handler.AnnotationRouterHandler;

import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 路由注册中心，负责注册所有路由模块
 */
public class RouterRegistry {
  private static final Logger logger = LoggerFactory.getLogger(RouterRegistry.class);
  private final Vertx vertx;
  private final JsonObject config;
  private final Injector injector;
  private final Router mainRouter;
  private static final String BASE_PACKAGE = "com.vertx.template";

  /**
   * 构造函数
   *
   * @param vertx  Vert.x实例
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

  /**
   * 注册全局中间件
   */
  private void registerMiddlewares() {
    // 通过注入器获取全局中间件
    GlobalMiddleware middleware = injector.getInstance(GlobalMiddleware.class);
    middleware.register();
  }

  /**
   * 注册基于注解的路由
   */
  private void registerAnnotationRoutes() {
    try {
      AnnotationRouterHandler annotationRouterHandler = injector.getInstance(AnnotationRouterHandler.class);
      annotationRouterHandler.registerRoutes(mainRouter);
      logger.info("基于注解的路由注册完成");
    } catch (Exception e) {
      logger.error("注册基于注解的路由失败", e);
    }
  }

  /**
   * 注册全局异常处理器
   */
  private void registerExceptionHandler() {
    mainRouter.errorHandler(404, ctx -> {
      ctx.response()
          .setStatusCode(200)
          .end(Json.encodePrettily(ApiResponse.error(404, "Not Found")));
    });
    mainRouter.errorHandler(405, ctx -> {
      ctx.response()
          .setStatusCode(200)
          .end(Json.encodePrettily(ApiResponse.error(405, "Method Not Allowed")));
    });
    mainRouter.route().failureHandler(ctx -> {
      ApiResponse<?> response;
      if (ctx.failure() instanceof BusinessException) {
        BusinessException ex = (BusinessException) ctx.failure();
        response = ApiResponse.error(ex.getCode(), ex.getMessage());
      } else {
        response = ApiResponse.error(500, ctx.failure() != null ? ctx.failure().getMessage() : "Internal Server Error");
      }
      ctx.response()
          .setStatusCode(200) // 强制HTTP状态码为200
          .putHeader("content-type", "application/json")
          .end(Json.encodePrettily(response));
    });

    logger.debug("全局异常处理器配置完成");
  }
}
