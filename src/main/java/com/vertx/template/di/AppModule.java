package com.vertx.template.di;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.vertx.template.config.DatabaseConfig;
import com.vertx.template.config.RouterConfig;
import com.vertx.template.middleware.GlobalMiddleware;
import com.vertx.template.middleware.auth.AuthenticationManager;
import com.vertx.template.middleware.core.impl.CorsMiddleware;
import com.vertx.template.middleware.ratelimit.core.RateLimitManager;
import com.vertx.template.middleware.ratelimit.interceptor.RateLimitInterceptor;
import com.vertx.template.repository.UserRepository;
import com.vertx.template.repository.impl.UserRepositoryImpl;
import com.vertx.template.service.UserService;
import com.vertx.template.service.impl.UserServiceImpl;
import com.vertx.template.router.cache.ReflectionCache;
import com.vertx.template.mq.MQManager;
import com.vertx.template.mq.config.RabbitMqConfig;
import com.vertx.template.mq.connection.ChannelPool;
import com.vertx.template.mq.connection.RabbitMqConnectionManager;
import com.vertx.template.mq.consumer.BasicConsumerMonitor;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;

/**
 * 依赖注入模块，配置应用程序的依赖关系
 * 遵循分层架构：Controller -> Service -> Repository
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
    // 绑定数据访问层接口到实现类
    bind(UserRepository.class).to(UserRepositoryImpl.class);

    // 绑定服务层接口到实现类（遵循分层架构）
    bind(UserService.class).to(UserServiceImpl.class);
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

  @Provides
  @Singleton
  public CorsMiddleware provideCorsMiddleware(JsonObject config) {
    return new CorsMiddleware(config);
  }

  @Provides
  @Singleton
  public GlobalMiddleware provideGlobalMiddleware(CorsMiddleware corsMiddleware) {
    return new GlobalMiddleware(corsMiddleware);
  }

  // ============== 消息队列相关依赖注入配置 ==============

  @Provides
  @Singleton
  public RabbitMqConfig provideRabbitMqConfig(final JsonObject config) {
    // 优先从mq.rabbitmq路径读取配置
    JsonObject mqConfig = config.getJsonObject("mq");
    JsonObject rabbitConfig = null;

    if (mqConfig != null) {
      rabbitConfig = mqConfig.getJsonObject("rabbitmq");
    }

    // 兼容旧配置，直接从rabbitmq路径读取
    if (rabbitConfig == null) {
      rabbitConfig = config.getJsonObject("rabbitmq", new JsonObject());
    }

    return RabbitMqConfig.fromJson(rabbitConfig);
  }

  @Provides
  @Singleton
  public RabbitMqConnectionManager provideRabbitMqConnectionManager(final Vertx vertx,
      final RabbitMqConfig rabbitMqConfig) {
    return new RabbitMqConnectionManager(vertx, rabbitMqConfig);
  }

  @Provides
  @Singleton
  public ChannelPool provideChannelPool(final Vertx vertx, final RabbitMqConnectionManager connectionManager) {
    return new ChannelPool(vertx, connectionManager);
  }

  @Provides
  @Singleton
  public BasicConsumerMonitor provideBasicConsumerMonitor() {
    return new BasicConsumerMonitor();
  }

  @Provides
  @Singleton
  public MQManager provideMQManager(
      final Vertx vertx,
      final RabbitMqConfig config,
      final com.google.inject.Injector injector,
      final BasicConsumerMonitor monitor,
      final ChannelPool channelPool) {
    return new MQManager(vertx, config, injector, monitor, channelPool);
  }
}
