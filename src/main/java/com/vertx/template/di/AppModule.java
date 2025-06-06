package com.vertx.template.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.vertx.template.config.DatabaseConfig;
import com.vertx.template.config.RouterConfig;
import com.vertx.template.middleware.auth.AuthenticationManager;
import com.vertx.template.middleware.ratelimit.core.RateLimitManager;
import com.vertx.template.middleware.ratelimit.interceptor.RateLimitInterceptor;
import com.vertx.template.repository.UserRepository;
import com.vertx.template.repository.impl.UserRepositoryImpl;
import com.vertx.template.router.cache.ReflectionCache;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

/** 依赖注入模块，配置应用程序的依赖关系 */
public class AppModule extends AbstractModule {

  private final Vertx vertx;
  private final JsonObject config;

  public AppModule(Vertx vertx, JsonObject config) {
    this.vertx = vertx;
    this.config = config;
  }

  @Override
  protected void configure() {
    // 绑定接口到实现类
    bind(UserRepository.class).to(UserRepositoryImpl.class);
  }

  @Provides
  @Singleton
  public Vertx provideVertx() {
    return vertx;
  }

  @Provides
  @Singleton
  public JsonObject provideConfig() {
    return config;
  }

  @Provides
  @Singleton
  public Router provideRouter() {
    return Router.router(vertx);
  }

  @Provides
  @Singleton
  public RouterConfig provideRouterConfig() {
    return new RouterConfig(config);
  }

  @Provides
  @Singleton
  public DatabaseConfig provideDatabaseConfig(Vertx vertx, JsonObject config) {
    return new DatabaseConfig(vertx, config);
  }

  @Provides
  @Singleton
  public AuthenticationManager provideAuthenticationManager(com.google.inject.Injector injector) {
    return new AuthenticationManager(injector);
  }

  @Provides
  @Singleton
  public RateLimitManager provideRateLimitManager() {
    return new RateLimitManager();
  }

  @Provides
  @Singleton
  public RateLimitInterceptor provideRateLimitInterceptor(RateLimitManager rateLimitManager) {
    return new RateLimitInterceptor(rateLimitManager);
  }

  @Provides
  @Singleton
  public ReflectionCache provideReflectionCache() {
    return new ReflectionCache();
  }
}
