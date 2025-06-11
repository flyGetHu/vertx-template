package com.vertx.template.verticle;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.vertx.template.di.AppModule;
import com.vertx.template.mq.MQManager;
import com.vertx.template.mq.config.RabbitMqConfig;
import com.vertx.template.mq.connection.ChannelPool;
import com.vertx.template.mq.connection.RabbitMqConnectionManager;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息队列Verticle 负责MQ连接初始化、消息发送接收基础设施和相关配置
 *
 * <p>
 * 主要职责： 1. 验证和加载MQ配置 2. 初始化RabbitMQ连接管理器 3. 自动扫描并启动消费者 4. 管理MQ服务的生命周期
 */
@Slf4j
public class MqVerticle extends AbstractVerticle {

  private static final String CONSUMER_SCAN_PACKAGE = "com.vertx.template";

  private boolean mqEnabled = false;
  private Injector injector;
  private MQManager mqManager;
  private RabbitMqConnectionManager connectionManager;
  private ChannelPool channelPool;

  @Override
  public void start(Promise<Void> startPromise) {
    try {
      log.info("开始启动MqVerticle...");

      final JsonObject config = context.config();

      // 1. 检查MQ是否启用
      if (!isMqEnabled(config)) {
        log.info("MQ功能未启用，跳过MQ初始化");
        startPromise.complete();
        return;
      }

      // 2. 验证MQ配置
      final RabbitMqConfig rabbitMqConfig = validateMqConfig(config);
      log.info(
          "MQ配置验证通过: host={}, port={}, user={}",
          rabbitMqConfig.getHost(),
          rabbitMqConfig.getPort(),
          rabbitMqConfig.getUser());

      // 3. 初始化依赖注入容器
      initializeDependencyInjection(config);

      // 4. 初始化连接管理器
      initializeConnectionManager();

      // 5. 初始化连接池
      initializeChannelPool();

      // 6. 启动消费者
      startConsumers();

      mqEnabled = true;
      log.info("MqVerticle启动成功 - MQ服务已就绪");
      startPromise.complete();

    } catch (Exception e) {
      log.error("MqVerticle启动失败：", e);
      startPromise.fail(e);
    }
  }

  @Override
  public void stop(Promise<Void> stopPromise) {
    if (!mqEnabled) {
      log.info("MQ未启用，无需停止");
      stopPromise.complete();
      return;
    }

    log.info("开始停止MqVerticle...");

    try {
      // 停止所有消费者
      if (mqManager != null) {
        mqManager.stopAllConsumers();
        log.info("所有消费者已停止");
      }

      // 关闭连接池
      if (channelPool != null) {
        channelPool.shutdown();
        log.info("连接池已关闭");
      }

      // 清理连接管理器
      if (connectionManager != null) {
        connectionManager.close();
        log.info("连接管理器已关闭");
      }

      log.info("MqVerticle停止完成");
      stopPromise.complete();

    } catch (Exception e) {
      log.error("停止MqVerticle时发生错误：", e);
      stopPromise.fail(e);
    }
  }

  /** 检查MQ是否启用 */
  private boolean isMqEnabled(final JsonObject config) {
    // 检查全局MQ开关
    final JsonObject mqConfig = config.getJsonObject("mq");
    final JsonObject rabbitConfig = mqConfig.getJsonObject("rabbitmq");
    if (rabbitConfig != null) {
      return rabbitConfig.getBoolean("enabled", false);
    }

    return false;
  }

  /** 验证MQ配置 */
  private RabbitMqConfig validateMqConfig(final JsonObject config) {
    // 优先从mq.rabbitmq路径读取配置
    JsonObject mqConfig = config.getJsonObject("mq");
    JsonObject rabbitConfig = null;

    if (mqConfig != null) {
      rabbitConfig = mqConfig.getJsonObject("rabbitmq");
    }

    // 兼容旧配置，直接从rabbitmq路径读取
    if (rabbitConfig == null) {
      rabbitConfig = config.getJsonObject("rabbitmq");
    }

    final RabbitMqConfig rabbitMqConfig = RabbitMqConfig.fromJson(rabbitConfig);

    if (!rabbitMqConfig.isValid()) {
      throw new IllegalArgumentException("RabbitMQ配置无效，请检查配置文件中的mq.rabbitmq或rabbitmq配置段");
    }

    return rabbitMqConfig;
  }

  /** 初始化依赖注入容器 */
  private void initializeDependencyInjection(final JsonObject config) {
    log.info("初始化MQ依赖注入容器...");

    try {
      injector = Guice.createInjector(new AppModule(vertx, config));
      mqManager = injector.getInstance(MQManager.class);
      connectionManager = injector.getInstance(RabbitMqConnectionManager.class);
      channelPool = injector.getInstance(ChannelPool.class);

      log.info("依赖注入容器初始化完成");
    } catch (Exception e) {
      throw new RuntimeException("初始化依赖注入容器失败", e);
    }
  }

  /** 初始化连接管理器 */
  private void initializeConnectionManager() {
    log.info("初始化RabbitMQ连接管理器...");

    try {
      connectionManager.initialize();
      log.info("RabbitMQ连接管理器初始化完成");
    } catch (Exception e) {
      throw new RuntimeException("初始化RabbitMQ连接管理器失败", e);
    }
  }

  /** 初始化连接池 */
  private void initializeChannelPool() {
    log.info("初始化连接池...");

    try {
      channelPool.initialize();
      log.info("连接池初始化完成 - {}", channelPool.getPoolStats());
    } catch (Exception e) {
      throw new RuntimeException("初始化连接池失败", e);
    }
  }

  /** 启动消费者 */
  private void startConsumers() {
    log.info("开始扫描并启动消费者...");

    try {
      // 自动扫描并启动所有消费者
      mqManager.scanAndStartConsumers(CONSUMER_SCAN_PACKAGE);

      final int activeCount = mqManager.getActiveConsumerCount();
      final int registeredCount = mqManager.getRegisteredConsumerCount();

      log.info("消费者启动完成 - 已注册: {}, 活跃: {}", registeredCount, activeCount);

      // 如果没有找到任何消费者，给出提示
      if (registeredCount == 0) {
        log.info(
            "未找到任何消费者，如需使用消费者功能，请在 {} 包下创建实现 MessageConsumer 接口并标注 @RabbitConsumer 的类",
            CONSUMER_SCAN_PACKAGE);
      }

    } catch (Exception e) {
      throw new RuntimeException("启动消费者失败", e);
    }
  }

  /** 获取MQ管理器（供其他组件使用） */
  public MQManager getMqManager() {
    return mqManager;
  }

  /** 检查MQ是否已启用并可用 */
  public boolean isMqReady() {
    return mqEnabled && mqManager != null && connectionManager != null;
  }
}
