package com.vertx.template.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.vertx.template.controller.UserController;
import com.vertx.template.handler.ResponseHandler;
import com.vertx.template.router.GlobalMiddleware;
import com.vertx.template.routes.UserRoutes;
import com.vertx.template.service.UserService;
import com.vertx.template.service.impl.UserServiceImpl;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

/**
 * Guice依赖注入模块
 */
public class AppModule extends AbstractModule {
  private final Vertx vertx;
  private final JsonObject config;

  public AppModule(Vertx vertx, JsonObject config) {
    this.vertx = vertx;
    this.config = config;
  }

  @Override
  protected void configure() {
    // 服务层绑定
    bind(UserService.class).to(UserServiceImpl.class).in(Singleton.class);

    // 控制器和路由绑定
    bind(UserController.class).in(Singleton.class);
    bind(UserRoutes.class).in(Singleton.class);

    // 中间件绑定
    bind(GlobalMiddleware.class).in(Singleton.class);

    // 响应处理器绑定
    bind(ResponseHandler.class).in(Singleton.class);

    // 绑定Vertx和Config
    bind(Vertx.class).toInstance(vertx);
    bind(JsonObject.class).toInstance(config);
  }

  @Provides
  @Singleton
  Router provideRouter() {
    return Router.router(vertx);
  }
}
