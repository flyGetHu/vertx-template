package com.vertx.template.mq.config;

import io.vertx.rabbitmq.RabbitMQOptions;
import lombok.Data;

/** RabbitMQ公共配置类 包含连接信息、连接池配置等通用设置 */
@Data
public class RabbitMqConfig {

  /** 默认主机地址 */
  private static final String DEFAULT_HOST = "localhost";

  /** 默认端口 */
  private static final int DEFAULT_PORT = 5672;

  /** 默认用户名 */
  private static final String DEFAULT_USERNAME = "guest";

  /** 默认密码 */
  private static final String DEFAULT_PASSWORD = "guest";

  /** 默认虚拟主机 */
  private static final String DEFAULT_VIRTUAL_HOST = "/";

  /** 默认连接池大小 */
  private static final int DEFAULT_POOL_SIZE = 10;

  /** 默认心跳间隔(秒) */
  private static final int DEFAULT_HEARTBEAT = 60;

  /** 默认连接超时(毫秒) */
  private static final int DEFAULT_CONNECTION_TIMEOUT = 60000;

  /** RabbitMQ服务器主机地址 */
  private String host = DEFAULT_HOST;

  /** RabbitMQ服务器端口 */
  private int port = DEFAULT_PORT;

  /** 用户名 */
  private String username = DEFAULT_USERNAME;

  /** 密码 */
  private String password = DEFAULT_PASSWORD;

  /** 虚拟主机 */
  private String virtualHost = DEFAULT_VIRTUAL_HOST;

  /** 连接池最大大小 */
  private int poolSize = DEFAULT_POOL_SIZE;

  /** 心跳间隔(秒) */
  private int heartbeat = DEFAULT_HEARTBEAT;

  /** 连接超时时间(毫秒) */
  private int connectionTimeout = DEFAULT_CONNECTION_TIMEOUT;

  /** 是否启用SSL */
  private boolean sslEnabled = false;

  /** 连接重试次数 */
  private int retryAttempts = 3;

  /** 连接重试间隔(毫秒) */
  private long retryDelay = 5000;

  /** 连接名称 */
  private String connectionName = "vertx-rabbitmq-client";

  /** 是否启用自动恢复 */
  private boolean automaticRecoveryEnabled = true;

  /** 网络恢复间隔(毫秒) */
  private long networkRecoveryInterval = 5000;

  /**
   * 验证配置是否有效
   *
   * @return 如果配置有效返回true，否则返回false
   */
  public boolean isValid() {
    return host != null
        && !host.trim().isEmpty()
        && port > 0
        && port <= 65535
        && username != null
        && !username.trim().isEmpty()
        && password != null
        && poolSize > 0
        && heartbeat >= 0
        && connectionTimeout > 0;
  }

  /**
   * 转换为Vert.x RabbitMQ配置选项
   *
   * @return RabbitMQOptions实例
   */
  public RabbitMQOptions toVertxOptions() {
    RabbitMQOptions options = new RabbitMQOptions();

    options.setHost(host);
    options.setPort(port);
    options.setUser(username);
    options.setPassword(password);
    options.setVirtualHost(virtualHost);
    options.setConnectionTimeout(connectionTimeout);
    options.setRequestedHeartbeat(heartbeat);
    options.setConnectionName(connectionName);
    options.setAutomaticRecoveryEnabled(automaticRecoveryEnabled);
    options.setNetworkRecoveryInterval(networkRecoveryInterval);

    if (sslEnabled) {
      options.setSsl(true);
    }

    return options;
  }

  /**
   * 获取连接URI
   *
   * @return RabbitMQ连接URI字符串
   */
  public String getConnectionUri() {
    String protocol = sslEnabled ? "amqps" : "amqp";
    return String.format(
        "%s://%s:%s@%s:%d%s", protocol, username, password, host, port, virtualHost);
  }
}
