package com.vertx.template.mq.connection;

import com.vertx.template.mq.config.RabbitMqConfig;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.rabbitmq.RabbitMQClient;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * 精简的RabbitMQ连接管理器
 *
 * 特性:
 * - 自动重连（指数退避策略）
 * - 连接健康检查
 * - 定时状态打印
 * - 抖动机制避免雷群效应
 */
@Slf4j
@Singleton
public class RabbitMqConnectionManager {

  private final Vertx vertx;
  private final RabbitMqConfig config;

  // 连接状态
  private final AtomicBoolean initialized = new AtomicBoolean(false);
  private final AtomicBoolean shutdown = new AtomicBoolean(false);
  private final AtomicBoolean connecting = new AtomicBoolean(false);
  private volatile RabbitMQClient client;

  // 重连配置 - 指数退避策略
  private static final int MAX_RETRY_ATTEMPTS = 15;
  private static final long INITIAL_RECONNECT_INTERVAL = 1000; // 初始间隔1秒
  private static final long MAX_RECONNECT_INTERVAL = 60000; // 最大间隔60秒
  private static final double BACKOFF_MULTIPLIER = 1.5; // 退避倍数
  private static final long HEALTH_CHECK_INTERVAL = 30000; // 健康检查30秒
  private static final long STATUS_PRINT_INTERVAL = 60000; // 状态打印60秒

  // 重连状态
  private final AtomicInteger retryCount = new AtomicInteger(0);
  private Long reconnectTimerId;
  private Long healthCheckTimerId;
  private Long statusPrintTimerId;

  /**
   * 构造器
   *
   * @param vertx  Vert.x实例
   * @param config RabbitMQ配置
   */
  @Inject
  public RabbitMqConnectionManager(final Vertx vertx, final RabbitMqConfig config) {
    this.vertx = vertx;
    this.config = config;
  }

  /** 初始化连接管理器 */
  public void initialize() {
    if (!initialized.compareAndSet(false, true)) {
      log.warn("连接管理器已经初始化，跳过重复初始化");
      return;
    }

    if (!config.isValid()) {
      throw new IllegalArgumentException("RabbitMQ配置无效");
    }

    log.info("正在初始化RabbitMQ连接管理器...");

    try {
      // 启动初始连接
      connectToRabbitMQ();

      // 启动定时任务
      startHealthCheck();
      startStatusPrinter();

      log.info("RabbitMQ连接管理器初始化完成");
    } catch (Exception e) {
      log.error("RabbitMQ连接管理器初始化失败", e);
      throw new RuntimeException("连接管理器初始化失败", e);
    }
  }

  /**
   * 获取RabbitMQ客户端
   *
   * @return RabbitMQ客户端对象
   * @throws IllegalStateException 如果连接不可用
   */
  public RabbitMQClient getClient() {
    if (!initialized.get()) {
      throw new IllegalStateException("连接管理器尚未初始化");
    }

    if (shutdown.get()) {
      throw new IllegalStateException("连接管理器已关闭");
    }

    if (client != null && client.isConnected()) {
      return client;
    }

    // 连接不可用时触发重连
    triggerReconnect();
    throw new IllegalStateException("RabbitMQ连接不可用，正在尝试重连中...");
  }

  /**
   * 检查连接是否可用
   *
   * @return 连接是否可用
   */
  public boolean isConnectionAvailable() {
    return initialized.get() && !shutdown.get() && client != null && client.isConnected();
  }

  /** 关闭连接管理器 */
  public void close() {
    if (!shutdown.compareAndSet(false, true)) {
      return;
    }

    log.info("正在关闭RabbitMQ连接管理器...");

    try {
      // 停止所有定时任务
      stopTimers();

      // 关闭连接
      closeConnection();

      initialized.set(false);
      log.info("RabbitMQ连接管理器已关闭");
    } catch (Exception e) {
      log.error("关闭RabbitMQ连接管理器时发生异常", e);
    }
  }

  // ================================
  // 私有方法
  // ================================

  /** 连接到RabbitMQ */
  private void connectToRabbitMQ() {
    if (shutdown.get() || connecting.get()) {
      return;
    }

    connecting.set(true);

    try {
      log.info(
          "尝试连接到RabbitMQ: {}:{} (第{}次尝试)",
          config.getHost(),
          config.getPort(),
          retryCount.get() + 1);

      // 关闭旧连接
      closeConnection();

      // 创建新连接
      client = RabbitMQClient.create(vertx, config.toVertxOptions());
      Future.await(client.start());

      if (client.isConnected()) {
        // 连接成功
        retryCount.set(0);
        connecting.set(false);
        log.info("RabbitMQ连接成功: {}:{}", config.getHost(), config.getPort());
        return;
      }
    } catch (Exception e) {
      log.error("连接RabbitMQ失败: {}:{} - {}", config.getHost(), config.getPort(), e.getMessage());
    }

    // 连接失败，安排重试
    connecting.set(false);
    scheduleReconnect();
  }

  /** 安排重连 */
  private void scheduleReconnect() {
    if (shutdown.get()) {
      return;
    }

    final int currentRetry = retryCount.getAndIncrement();

    if (currentRetry >= MAX_RETRY_ATTEMPTS) {
      final long resetInterval = MAX_RECONNECT_INTERVAL * 2; // 2分钟后重置
      log.error(
          "RabbitMQ重连失败，已达到最大重试次数: {}，将在{}ms后重置重试计数",
          MAX_RETRY_ATTEMPTS, resetInterval);

      // 重置重试计数后继续尝试
      vertx.setTimer(resetInterval, id -> {
        retryCount.set(0);
        scheduleReconnect();
      });
      return;
    }

    // 计算动态重连间隔（指数退避 + 抖动）
    final long reconnectInterval = calculateReconnectInterval(currentRetry);
    log.debug("将在{}ms后进行第{}次重连尝试", reconnectInterval, currentRetry + 1);

    reconnectTimerId = vertx.setTimer(reconnectInterval, id -> {
      reconnectTimerId = null;
      connectToRabbitMQ();
    });
  }

  /**
   * 计算重连间隔（指数退避策略 + 抖动）
   *
   * @param retryAttempt 重试次数
   * @return 重连间隔（毫秒）
   */
  private long calculateReconnectInterval(final int retryAttempt) {
    // 基础间隔 = 初始间隔 * (退避倍数 ^ 重试次数)
    double baseInterval = INITIAL_RECONNECT_INTERVAL * Math.pow(BACKOFF_MULTIPLIER, retryAttempt);

    // 限制最大间隔
    baseInterval = Math.min(baseInterval, MAX_RECONNECT_INTERVAL);

    // 添加抖动（±20%的随机波动），避免雷群效应
    final double jitterFactor = 0.8 + (Math.random() * 0.4); // 0.8 - 1.2
    final long finalInterval = (long) (baseInterval * jitterFactor);

    return Math.max(finalInterval, INITIAL_RECONNECT_INTERVAL);
  }

  /** 触发重连 */
  private void triggerReconnect() {
    if (shutdown.get() || connecting.get()) {
      return;
    }

    // 取消已安排的重连任务
    cancelReconnectTimer();

    // 立即尝试重连
    vertx.runOnContext(v -> connectToRabbitMQ());
  }

  /** 启动连接健康检查 */
  private void startHealthCheck() {
    healthCheckTimerId = vertx.setPeriodic(
        HEALTH_CHECK_INTERVAL,
        id -> {
          try {
            if (shutdown.get()) {
              vertx.cancelTimer(id);
              return;
            }

            // 检查连接状态
            if (client == null || !client.isConnected()) {
              log.debug("健康检查发现连接断开，触发重连");
              triggerReconnect();
            }
          } catch (Exception e) {
            log.error("健康检查执行异常", e);
          }
        });

    log.debug("连接健康检查已启动，间隔: {}ms", HEALTH_CHECK_INTERVAL);
  }

  /** 启动状态打印器 */
  private void startStatusPrinter() {
    statusPrintTimerId = vertx.setPeriodic(
        STATUS_PRINT_INTERVAL,
        id -> {
          try {
            if (shutdown.get()) {
              vertx.cancelTimer(id);
              return;
            }

            printConnectionStatus();
          } catch (Exception e) {
            log.error("状态打印执行异常", e);
          }
        });

    log.debug("连接状态打印器已启动，间隔: {}ms", STATUS_PRINT_INTERVAL);
  }

  /** 打印连接状态 */
  private void printConnectionStatus() {
    if (client != null && client.isConnected()) {
      log.info("RabbitMQ连接状态: 已连接 - {}:{}", config.getHost(), config.getPort());
    } else {
      log.warn(
          "RabbitMQ连接状态: 未连接 - {}:{}, 重试次数: {}, 正在连接: {}",
          config.getHost(),
          config.getPort(),
          retryCount.get(),
          connecting.get());
    }
  }

  /** 关闭连接 */
  private void closeConnection() {
    if (client != null) {
      try {
        Future.await(client.stop());
        log.debug("RabbitMQ连接已关闭");
      } catch (Exception e) {
        log.warn("关闭RabbitMQ连接时发生异常: {}", e.getMessage());
      } finally {
        client = null;
      }
    }
  }

  /** 停止所有定时器 */
  private void stopTimers() {
    cancelReconnectTimer();

    if (healthCheckTimerId != null) {
      vertx.cancelTimer(healthCheckTimerId);
      healthCheckTimerId = null;
    }

    if (statusPrintTimerId != null) {
      vertx.cancelTimer(statusPrintTimerId);
      statusPrintTimerId = null;
    }
  }

  /** 取消重连定时器 */
  private void cancelReconnectTimer() {
    if (reconnectTimerId != null) {
      vertx.cancelTimer(reconnectTimerId);
      reconnectTimerId = null;
    }
  }
}
