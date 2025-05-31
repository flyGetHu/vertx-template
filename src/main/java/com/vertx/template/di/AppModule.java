package com.vertx.template.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.vertx.template.config.RouterConfig;
import com.vertx.template.middleware.auth.AuthMiddleware;
import com.vertx.template.middleware.auth.AuthenticationManager;
import com.vertx.template.middleware.common.MiddlewareChain;
import com.vertx.template.middleware.ratelimit.RateLimitMiddleware;
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

    // 其他绑定配置
  }

  @Provides
  @Singleton
  Vertx provideVertx() {
    return vertx;
  }

  @Provides
  @Singleton
  JsonObject provideConfig() {
    return config;
  }

  @Provides
  @Singleton
  Router provideRouter() {
    return Router.router(vertx);
  }

  @Provides
  @Singleton
  public RouterConfig provideRouterConfig() {
    return new RouterConfig(config);
  }

  @Provides
  @Singleton
  public ReflectionCache provideReflectionCache() {
    return new ReflectionCache();
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
  public MiddlewareChain provideMiddlewareChain() {
    return new MiddlewareChain();
  }

  @Provides
  @Singleton
  public AuthMiddleware provideAuthMiddleware(AuthenticationManager authenticationManager) {
    return new com.vertx.template.middleware.auth.impl.DefaultAuthMiddleware(authenticationManager);
  }

  @Provides
  @Singleton
  public RateLimitMiddleware provideRateLimitMiddleware(RateLimitManager rateLimitManager) {
    return new com.vertx.template.middleware.ratelimit.impl.DefaultRateLimitMiddleware(
        rateLimitManager);
  }
}
