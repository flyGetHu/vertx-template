package com.vertx.template.mq.connection;

import com.vertx.template.mq.config.RabbitMqConfig;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.rabbitmq.RabbitMQClient;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/** RabbitMQ连接管理器 基于Vert.x RabbitMQ客户端，管理连接的创建和维护 */
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
  public void initialize() {
    if (initialized.get()) {
      return;
    }

    log.info("正在初始化RabbitMQ连接管理器...");

    if (!config.isValid()) {
      throw new IllegalArgumentException("RabbitMQ配置无效");
    }

    try {
      // 创建RabbitMQ客户端
      client = RabbitMQClient.create(vertx, config.toVertxOptions());

      // 启动客户端连接
      Future.await(client.start());
      // 测试连接
      if (!client.isConnected()) {
        throw new RuntimeException("RabbitMQ连接失败");
      }
      initialized.set(true);
      log.info("RabbitMQ连接管理器初始化完成");
    } catch (Exception e) {
      log.error("创建RabbitMQ客户端失败", e);
      throw new RuntimeException("创建RabbitMQ客户端失败", e);
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
  public void close() {
    log.info("正在关闭RabbitMQ连接管理器...");

    if (client != null && client.isConnected()) {
      Future.await(client.stop());
      initialized.set(false);
      log.info("RabbitMQ连接已关闭");
    } else {
      log.warn("RabbitMQ连接已关闭");
    }
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
      return String.format("RabbitMQ连接状态: 已连接 - %s:%d", config.getHost(), config.getPort());
    } else {
      return "RabbitMQ连接状态: 未连接";
    }
  }
}
