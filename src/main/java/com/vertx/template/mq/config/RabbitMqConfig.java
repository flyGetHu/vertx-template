package com.vertx.template.mq.config;

import io.vertx.core.json.JsonObject;
import io.vertx.rabbitmq.RabbitMQOptions;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

@Data
@NoArgsConstructor
@Accessors(chain = true)
public class RabbitMqConfig {
  private String host = "localhost";
  private int port = 5672;
  private String user = "guest";
  private String password = "guest";
  private String virtualHost = "/";
  private boolean useSsl = false;
  private int connectionTimeout = 60000;
  private int requestedHeartbeat = 60;
  private int handshakeTimeout = 10000;
  private int requestedChannelMax = 5;
  private long networkRecoveryInterval = 5000;
  private boolean automaticRecovery = true;

  /** 连接重试配置 */
  private int maxRetryAttempts = 10;

  private long initialRetryDelay = 1000; // 1秒
  private long maxRetryDelay = 30000; // 30秒
  private long connectionCheckInterval = 10000; // 10秒

  /** 连接池配置 */
  private ChannelPoolConfig poolConfig = new ChannelPoolConfig();

  public static RabbitMqConfig fromJson(JsonObject json) {
    if (json == null) {
      return new RabbitMqConfig();
    }
    return new RabbitMqConfig()
        .setHost(json.getString("host", "localhost"))
        .setPort(json.getInteger("port", 5672))
        .setUser(json.getString("user", "guest"))
        .setPassword(json.getString("password", "guest"))
        .setVirtualHost(json.getString("virtualHost", "/"))
        .setUseSsl(json.getBoolean("useSsl", false))
        .setConnectionTimeout(json.getInteger("connectionTimeout", 60000))
        .setRequestedHeartbeat(json.getInteger("requestedHeartbeat", 60))
        .setHandshakeTimeout(json.getInteger("handshakeTimeout", 10000))
        .setRequestedChannelMax(json.getInteger("requestedChannelMax", 5))
        .setNetworkRecoveryInterval(json.getLong("networkRecoveryInterval", 5000L))
        .setAutomaticRecovery(json.getBoolean("automaticRecovery", true))
        .setMaxRetryAttempts(json.getInteger("maxRetryAttempts", 10))
        .setInitialRetryDelay(json.getLong("initialRetryDelay", 1000L))
        .setMaxRetryDelay(json.getLong("maxRetryDelay", 30000L))
        .setConnectionCheckInterval(json.getLong("connectionCheckInterval", 10000L))
        .setPoolConfig(ChannelPoolConfig.fromJson(json.getJsonObject("pool")));
  }

  /**
   * 验证配置是否有效
   *
   * @return 配置是否有效
   */
  public boolean isValid() {
    if (host == null || host.trim().isEmpty()) {
      return false;
    }
    if (port <= 0 || port > 65535) {
      return false;
    }
    if (user == null || user.trim().isEmpty()) {
      return false;
    }
    if (password == null) {
      return false;
    }
    if (virtualHost == null) {
      return false;
    }
    if (connectionTimeout <= 0) {
      return false;
    }
    if (requestedHeartbeat < 0) {
      return false;
    }
    if (handshakeTimeout <= 0) {
      return false;
    }
    if (requestedChannelMax <= 0) {
      return false;
    }
    if (networkRecoveryInterval < 0) {
      return false;
    }
    return true;
  }

  /**
   * 转换为 Vert.x RabbitMQ 配置选项
   *
   * @return RabbitMQOptions
   */
  public RabbitMQOptions toVertxOptions() {
    return new RabbitMQOptions()
        .setHost(host)
        .setPort(port)
        .setUser(user)
        .setPassword(password)
        .setVirtualHost(virtualHost)
        .setSsl(useSsl)
        .setConnectionTimeout(connectionTimeout)
        .setRequestedHeartbeat(requestedHeartbeat)
        .setHandshakeTimeout(handshakeTimeout)
        .setRequestedChannelMax(requestedChannelMax)
        .setNetworkRecoveryInterval(networkRecoveryInterval)
        .setAutomaticRecoveryEnabled(automaticRecovery);
  }
}
