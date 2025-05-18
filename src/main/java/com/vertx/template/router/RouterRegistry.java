package com.vertx.template.router;

import com.vertx.template.controller.UserController;
import com.vertx.template.exception.BusinessException;
import com.vertx.template.model.ApiResponse;
import com.vertx.template.routes.UserRoutes;
import com.vertx.template.service.UserService;
import com.vertx.template.service.UserServiceImpl;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 路由注册中心，负责注册所有路由模块
 */
public class RouterRegistry {
  private static final Logger logger = LoggerFactory.getLogger(RouterRegistry.class);
  private final Vertx vertx;
  private final Router mainRouter;
  private final JsonObject config;
  private final List<RouteGroup> routeGroups = new ArrayList<>();

  /**
   * 构造函数
   *
   * @param vertx  Vert.x实例
   * @param config 应用配置
   */
  public RouterRegistry(Vertx vertx, JsonObject config) {
    this.vertx = vertx;
    this.config = config;
    this.mainRouter = Router.router(vertx);

    // 初始化路由组
    initRouteGroups();
  }

  /**
   * 初始化所有路由组
   */
  private void initRouteGroups() {
    // 添加用户路由组
    UserService userService = new UserServiceImpl();
    UserController userController = new UserController(userService);
    routeGroups.add(new UserRoutes(userController));

    // 此处可以添加更多路由组...
    // 例如：routeGroups.add(new ProductRoutes(productController));
  }

  /**
   * 注册所有路由模块
   *
   * @return 配置好的Router实例
   */
  public Router registerAll() {
    // 注册全局中间件
    registerMiddlewares();

    // 注册业务路由
    registerBusinessRoutes();

    // 注册错误处理（应放在最后）
    registerExceptionHandler();

    return mainRouter;
  }

  /**
   * 注册全局中间件
   */
  private void registerMiddlewares() {
    // 注册全局中间件
    new GlobalMiddleware(vertx, mainRouter, config).register();
  }

  /**
   * 注册业务路由
   */
  private void registerBusinessRoutes() {
    // 注册所有路由组
    for (RouteGroup group : routeGroups) {
      group.register(mainRouter);
    }

    logger.info("所有业务路由注册完成");
  }

  /**
   * 注册全局异常处理器
   */
  private void registerExceptionHandler() {
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
