package com.vertx.template.mq.connection;

import com.vertx.template.mq.config.RabbitMqConfig;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.rabbitmq.RabbitMQClient;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RabbitMQ连接管理器
 * 基于Vert.x RabbitMQ客户端，管理连接的创建和维护
 */
@Slf4j
@Singleton
public class RabbitMqConnectionManager {

  private final Vertx vertx;
  private final RabbitMqConfig config;
  private final AtomicBoolean initialized = new AtomicBoolean(false);
  private volatile RabbitMQClient client;

  /**
   * 构造器
   *
   * @param vertx  Vert.x实例
   * @param config RabbitMQ配置
   */
  @Inject
  public RabbitMqConnectionManager(Vertx vertx, RabbitMqConfig config) {
    this.vertx = vertx;
    this.config = config;
  }

  /**
   * 初始化连接管理器
   *
   * @return 初始化结果的Future
   */
  public Future<Void> initialize() {
    if (initialized.get()) {
      return Future.succeededFuture();
    }

    log.info("正在初始化RabbitMQ连接管理器...");

    if (!config.isValid()) {
      return Future.failedFuture(new IllegalArgumentException("RabbitMQ配置无效"));
    }

    try {
      // 创建RabbitMQ客户端
      client = RabbitMQClient.create(vertx, config.toVertxOptions());

      // 启动客户端连接
      return client.start()
          .onSuccess(v -> {
            initialized.set(true);
            log.info("RabbitMQ连接管理器初始化完成");
          })
          .onFailure(cause -> {
            log.error("初始化RabbitMQ连接管理器失败", cause);
          });
    } catch (Exception e) {
      log.error("创建RabbitMQ客户端失败", e);
      return Future.failedFuture(e);
    }
  }

  /**
   * 获取RabbitMQ客户端
   *
   * @return RabbitMQ客户端对象
   */
  public RabbitMQClient getClient() {
    if (!initialized.get()) {
      throw new IllegalStateException("连接管理器尚未初始化");
    }

    if (client == null || !client.isConnected()) {
      throw new IllegalStateException("RabbitMQ连接不可用");
    }

    return client;
  }

  /**
   * 关闭连接管理器
   *
   * @return 关闭结果的Future
   */
  public Future<Void> close() {
    log.info("正在关闭RabbitMQ连接管理器...");

    if (client != null && client.isConnected()) {
      return client.stop()
          .onSuccess(v -> {
            initialized.set(false);
            log.info("RabbitMQ连接已关闭");
          })
          .onFailure(cause -> {
            log.error("关闭RabbitMQ连接失败", cause);
          });
    }

    initialized.set(false);
    return Future.succeededFuture();
  }

  /**
   * 检查连接是否可用
   *
   * @return 连接是否可用
   */
  public boolean isConnectionAvailable() {
    return initialized.get() && client != null && client.isConnected();
  }

  /**
   * 获取连接信息
   *
   * @return 连接信息字符串
   */
  public String getConnectionInfo() {
    if (client != null && client.isConnected()) {
      return String.format("RabbitMQ连接状态: 已连接 - %s:%d",
          config.getHost(), config.getPort());
    } else {
      return "RabbitMQ连接状态: 未连接";
    }
  }
}
