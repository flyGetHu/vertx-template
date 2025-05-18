package com.vertx.template.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import javax.inject.Singleton;
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
    // 只绑定无法通过注解自动注入的实例
    bind(Vertx.class).toInstance(vertx);
    bind(JsonObject.class).toInstance(config);
  }

  @Provides
  @Singleton
  Router provideRouter() {
    return Router.router(vertx);
  }
}
