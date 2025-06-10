package com.vertx.template.mq.consumer.lifecycle;

import com.vertx.template.mq.consumer.ConsumerManager;
import com.vertx.template.mq.consumer.ConsumerMonitor;
import com.vertx.template.mq.consumer.ConsumerRegistry;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/** 消费者生命周期管理器 负责消费者的优雅启停、健康检查和资源管理 */
@Slf4j
@Singleton
public class ConsumerLifecycleManager {

  private final Vertx vertx;
  private final ConsumerRegistry consumerRegistry;
  private final ConsumerManager consumerManager;
  private final ConsumerMonitor consumerMonitor;

  private final AtomicBoolean isRunning = new AtomicBoolean(false);
  private final AtomicBoolean isShuttingDown = new AtomicBoolean(false);

  private volatile long healthCheckTimerId = -1;

  /** 默认健康检查间隔（毫秒） */
  private static final long DEFAULT_HEALTH_CHECK_INTERVAL = 30000; // 30秒

  /** 默认监控间隔（毫秒） */
  private static final long DEFAULT_MONITOR_INTERVAL = 60000; // 1分钟

  /**
   * 构造器
   *
   * @param vertx Vert.x实例
   * @param consumerRegistry 消费者注册器
   * @param consumerManager 消费者管理器
   * @param consumerMonitor 消费者监控器
   */
  @Inject
  public ConsumerLifecycleManager(
      Vertx vertx,
      ConsumerRegistry consumerRegistry,
      ConsumerManager consumerManager,
      ConsumerMonitor consumerMonitor) {
    this.vertx = vertx;
    this.consumerRegistry = consumerRegistry;
    this.consumerManager = consumerManager;
    this.consumerMonitor = consumerMonitor;
  }

  /**
   * 启动消费者系统
   *
   * @param basePackage 消费者扫描包路径
   */
  public void start(String basePackage) {
    start(basePackage, DEFAULT_HEALTH_CHECK_INTERVAL, DEFAULT_MONITOR_INTERVAL);
  }

  /**
   * 启动消费者系统
   *
   * @param basePackage 消费者扫描包路径
   * @param healthCheckInterval 健康检查间隔（毫秒）
   * @param monitorInterval 监控间隔（毫秒）
   */
  public void start(String basePackage, long healthCheckInterval, long monitorInterval) {
    if (!isRunning.compareAndSet(false, true)) {
      log.warn("消费者系统已经在运行中");
      return;
    }

    log.info("正在启动消费者生命周期管理系统...");

    try {
      // 1. 启动监控系统
      Future.await(consumerMonitor.startMonitoring(monitorInterval));

      // 2. 扫描并注册消费者
      consumerRegistry.scanAndRegisterConsumers(basePackage);

      // 3. 启动健康检查
      startHealthCheck(healthCheckInterval);

      // 4. 注册关闭钩子
      registerShutdownHook();

      log.info("消费者生命周期管理系统启动成功");
      log.info(
          "已注册消费者数量: {}, 活跃消费者数量: {}",
          consumerRegistry.getRegisteredConsumerCount(),
          consumerRegistry.getActiveConsumerCount());

    } catch (Exception cause) {
      log.error("消费者生命周期管理系统启动失败", cause);
      isRunning.set(false);
      // 清理已启动的组件
      cleanup();
      throw new RuntimeException("消费者生命周期管理系统启动失败", cause);
    }
  }

  /** 停止消费者系统 */
  public void stop() {
    if (!isRunning.get()) {
      log.warn("消费者系统未运行");
      return;
    }

    if (!isShuttingDown.compareAndSet(false, true)) {
      log.warn("消费者系统正在关闭中");
      return;
    }

    log.info("正在优雅关闭消费者生命周期管理系统...");

    try {
      // 1. 停止健康检查
      stopHealthCheck();

      // 2. 停止所有消费者
      consumerRegistry.stopAllConsumers();

      // 3. 停止监控系统
      Future.await(consumerMonitor.stopMonitoring());

      isRunning.set(false);
      isShuttingDown.set(false);
      log.info("消费者生命周期管理系统已优雅关闭");

    } catch (Exception cause) {
      log.error("消费者生命周期管理系统关闭失败", cause);
      isShuttingDown.set(false);
      throw new RuntimeException("消费者生命周期管理系统关闭失败", cause);
    }
  }

  /**
   * 重启消费者系统
   *
   * @param basePackage 消费者扫描包路径
   */
  public void restart(String basePackage) {
    log.info("正在重启消费者生命周期管理系统...");

    try {
      stop();

      // 等待一段时间确保资源完全释放
      Thread.sleep(1000);

      start(basePackage);

      log.info("消费者生命周期管理系统重启成功");
    } catch (Exception cause) {
      log.error("消费者生命周期管理系统重启失败", cause);
      throw new RuntimeException("消费者生命周期管理系统重启失败", cause);
    }
  }

  /**
   * 获取系统健康状态
   *
   * @return 系统健康状态
   */
  public JsonObject getHealthStatus() {
    final JsonObject health = new JsonObject();

    health.put("running", isRunning.get());
    health.put("shuttingDown", isShuttingDown.get());
    health.put("registeredConsumers", consumerRegistry.getRegisteredConsumerCount());
    health.put("activeConsumers", consumerRegistry.getActiveConsumerCount());

    // 获取所有消费者的监控指标
    final JsonObject metrics = consumerMonitor.getAllMetrics();
    health.put("metrics", metrics);

    // 计算整体健康分数
    final double healthScore = calculateHealthScore();
    health.put("healthScore", healthScore);

    final String status;
    if (!isRunning.get()) {
      status = "STOPPED";
    } else if (isShuttingDown.get()) {
      status = "SHUTTING_DOWN";
    } else if (healthScore >= 0.9) {
      status = "HEALTHY";
    } else if (healthScore >= 0.7) {
      status = "WARNING";
    } else {
      status = "UNHEALTHY";
    }
    health.put("status", status);

    return health;
  }

  /**
   * 检查系统是否正在运行
   *
   * @return 是否正在运行
   */
  public boolean isRunning() {
    return isRunning.get();
  }

  /**
   * 检查系统是否正在关闭
   *
   * @return 是否正在关闭
   */
  public boolean isShuttingDown() {
    return isShuttingDown.get();
  }

  /**
   * 启动健康检查
   *
   * @param interval 检查间隔（毫秒）
   */
  private void startHealthCheck(long interval) {
    log.info("启动健康检查，间隔: {}ms", interval);

    healthCheckTimerId =
        vertx.setPeriodic(
            interval,
            id -> {
              try {
                performHealthCheck();
              } catch (Exception e) {
                log.error("健康检查执行失败", e);
              }
            });
  }

  /** 停止健康检查 */
  private void stopHealthCheck() {
    if (healthCheckTimerId != -1) {
      vertx.cancelTimer(healthCheckTimerId);
      healthCheckTimerId = -1;
      log.info("健康检查已停止");
    }
  }

  /** 执行健康检查 */
  private void performHealthCheck() {
    final JsonObject health = getHealthStatus();
    final String status = health.getString("status");

    if (log.isDebugEnabled()) {
      log.debug("系统健康检查结果: {}", health.encode());
    }

    // 根据健康状态采取相应行动
    switch (status) {
      case "UNHEALTHY":
        log.error("系统健康状态异常: {}", health.encode());
        handleUnhealthyStatus(health);
        break;
      case "WARNING":
        log.warn("系统健康状态警告: {}", health.encode());
        handleWarningStatus(health);
        break;
      case "HEALTHY":
        if (log.isTraceEnabled()) {
          log.trace("系统健康状态良好");
        }
        break;
    }
  }

  /**
   * 处理异常健康状态
   *
   * @param health 健康状态信息
   */
  private void handleUnhealthyStatus(JsonObject health) {
    // 可以在这里实现自动恢复逻辑
    // 例如：重启失败的消费者、发送告警等
    log.warn("检测到系统不健康，建议检查消费者状态并考虑重启");
  }

  /**
   * 处理警告健康状态
   *
   * @param health 健康状态信息
   */
  private void handleWarningStatus(JsonObject health) {
    // 可以在这里实现预警逻辑
    log.info("系统健康状态处于警告状态，请关注相关指标");
  }

  /**
   * 计算系统健康分数
   *
   * @return 健康分数（0.0 - 1.0）
   */
  private double calculateHealthScore() {
    final int registeredCount = consumerRegistry.getRegisteredConsumerCount();
    final int activeCount = consumerRegistry.getActiveConsumerCount();

    if (registeredCount == 0) {
      return 1.0; // 没有消费者也算正常
    }

    // 基础分数：活跃消费者比例
    final double baseScore = (double) activeCount / registeredCount;

    // 根据监控指标调整分数
    final JsonObject metrics = consumerMonitor.getAllMetrics();
    // 这里可以根据具体的监控指标进一步计算健康分数

    return Math.max(0.0, Math.min(1.0, baseScore));
  }

  /** 注册关闭钩子 */
  private void registerShutdownHook() {
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  log.info("检测到系统关闭信号，正在优雅关闭消费者系统...");
                  try {
                    stop();
                    log.info("消费者系统已优雅关闭完成");
                  } catch (Exception e) {
                    log.error("消费者系统关闭失败", e);
                  }
                }));

    log.info("已注册系统关闭钩子");
  }

  /** 清理资源 */
  private void cleanup() {
    try {
      stopHealthCheck();
      Future.await(consumerMonitor.stopMonitoring());
      consumerRegistry.stopAllConsumers();
    } catch (Exception e) {
      log.error("清理资源失败", e);
    }
  }
}
