package com.vertx.template.controller;

import com.vertx.template.mq.consumer.ConsumerMonitor;
import com.vertx.template.mq.consumer.ConsumerRegistry;
import com.vertx.template.mq.consumer.lifecycle.ConsumerLifecycleManager;
import com.vertx.template.router.annotation.RestController;
import com.vertx.template.router.annotation.GetMapping;
import com.vertx.template.router.annotation.PostMapping;
import com.vertx.template.router.annotation.PathParam;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * 消费者管理控制器
 * 提供消费者状态查询、管理和监控相关的API接口
 */
@Slf4j
@RestController("/api/consumer")
@Singleton
public class ConsumerManagementController {

  private final ConsumerRegistry consumerRegistry;
  private final ConsumerMonitor consumerMonitor;
  private final ConsumerLifecycleManager lifecycleManager;

  /**
   * 构造器
   *
   * @param consumerRegistry 消费者注册器
   * @param consumerMonitor  消费者监控器
   * @param lifecycleManager 生命周期管理器
   */
  @Inject
  public ConsumerManagementController(ConsumerRegistry consumerRegistry,
      ConsumerMonitor consumerMonitor,
      ConsumerLifecycleManager lifecycleManager) {
    this.consumerRegistry = consumerRegistry;
    this.consumerMonitor = consumerMonitor;
    this.lifecycleManager = lifecycleManager;
  }

  /**
   * 获取系统健康状态
   *
   * @return 系统健康状态
   */
  @GetMapping("/health")
  public JsonObject getHealthStatus() {
    log.debug("获取消费者系统健康状态");
    return lifecycleManager.getHealthStatus();
  }

  /**
   * 获取所有消费者的监控指标
   *
   * @return 监控指标
   */
  @GetMapping("/metrics")
  public JsonObject getAllMetrics() {
    log.debug("获取所有消费者监控指标");
    return consumerMonitor.getAllMetrics();
  }

  /**
   * 获取指定消费者的健康状态
   *
   * @param consumerName 消费者名称
   * @return 消费者健康状态
   */
  @GetMapping("/health/{consumerName}")
  public JsonObject getConsumerHealth(@PathParam("consumerName") String consumerName) {
    log.debug("获取消费者 {} 的健康状态", consumerName);

    JsonObject health = consumerMonitor.checkConsumerHealth(consumerName);
    if (health.getString("status").equals("UNKNOWN")) {
      // 补充注册状态信息
      health.put("registered", consumerRegistry.isConsumerRegistered(consumerName));
      health.put("active", consumerRegistry.isConsumerActive(consumerName));
    }

    return health;
  }

  /**
   * 获取指定消费者的详细指标
   *
   * @param consumerName 消费者名称
   * @return 消费者详细指标
   */
  @GetMapping("/metrics/{consumerName}")
  public JsonObject getConsumerMetrics(@PathParam("consumerName") String consumerName) {
    log.debug("获取消费者 {} 的详细指标", consumerName);

    ConsumerMonitor.ConsumerMetrics metrics = consumerMonitor.getConsumerMetrics(consumerName);
    if (metrics == null) {
      return new JsonObject()
          .put("error", "消费者未找到或无监控数据")
          .put("consumerName", consumerName)
          .put("registered", consumerRegistry.isConsumerRegistered(consumerName))
          .put("active", consumerRegistry.isConsumerActive(consumerName));
    }

    return new JsonObject()
        .put("consumerName", metrics.getConsumerName())
        .put("successCount", metrics.getSuccessCount())
        .put("failureCount", metrics.getFailureCount())
        .put("retryCount", metrics.getRetryCount())
        .put("totalCount", metrics.getTotalCount())
        .put("successRate", metrics.getSuccessRate())
        .put("avgProcessingTime", metrics.getAvgProcessingTime())
        .put("maxProcessingTime", metrics.getMaxProcessingTime())
        .put("minProcessingTime", metrics.getMinProcessingTime())
        .put("maxRetryCount", metrics.getMaxRetryCount())
        .put("lastSuccessTime", metrics.getLastSuccessTime())
        .put("lastFailureTime", metrics.getLastFailureTime())
        .put("recentFailures", JsonObject.mapFrom(metrics.getRecentFailures()))
        .put("registered", consumerRegistry.isConsumerRegistered(consumerName))
        .put("active", consumerRegistry.isConsumerActive(consumerName));
  }

  /**
   * 重启指定消费者
   *
   * @param consumerName 消费者名称
   * @return 操作结果
   */
  @PostMapping("/restart/{consumerName}")
  public JsonObject restartConsumer(@PathParam("consumerName") String consumerName) {
    log.info("重启消费者: {}", consumerName);

    if (!consumerRegistry.isConsumerRegistered(consumerName)) {
      return new JsonObject()
          .put("success", false)
          .put("message", "消费者未注册")
          .put("consumerName", consumerName);
    }

    try {
      // 使用Future.await在虚拟线程中同步等待
      Future.await(consumerRegistry.restartConsumer(consumerName));

      return new JsonObject()
          .put("success", true)
          .put("message", "消费者重启成功")
          .put("consumerName", consumerName);

    } catch (Exception e) {
      log.error("重启消费者 {} 失败", consumerName, e);
      return new JsonObject()
          .put("success", false)
          .put("message", "消费者重启失败: " + e.getMessage())
          .put("consumerName", consumerName);
    }
  }

  /**
   * 停止指定消费者
   *
   * @param consumerName 消费者名称
   * @return 操作结果
   */
  @PostMapping("/stop/{consumerName}")
  public JsonObject stopConsumer(@PathParam("consumerName") String consumerName) {
    log.info("停止消费者: {}", consumerName);

    if (!consumerRegistry.isConsumerRegistered(consumerName)) {
      return new JsonObject()
          .put("success", false)
          .put("message", "消费者未注册")
          .put("consumerName", consumerName);
    }

    try {
      // 使用Future.await在虚拟线程中同步等待
      Future.await(consumerRegistry.unregisterConsumer(consumerName));

      return new JsonObject()
          .put("success", true)
          .put("message", "消费者停止成功")
          .put("consumerName", consumerName);

    } catch (Exception e) {
      log.error("停止消费者 {} 失败", consumerName, e);
      return new JsonObject()
          .put("success", false)
          .put("message", "消费者停止失败: " + e.getMessage())
          .put("consumerName", consumerName);
    }
  }

  /**
   * 重置指定消费者的监控指标
   *
   * @param consumerName 消费者名称
   * @return 操作结果
   */
  @PostMapping("/metrics/reset/{consumerName}")
  public JsonObject resetConsumerMetrics(@PathParam("consumerName") String consumerName) {
    log.info("重置消费者 {} 的监控指标", consumerName);

    consumerMonitor.resetMetrics(consumerName);

    return new JsonObject()
        .put("success", true)
        .put("message", "监控指标重置成功")
        .put("consumerName", consumerName);
  }

  /**
   * 重置所有消费者的监控指标
   *
   * @return 操作结果
   */
  @PostMapping("/metrics/reset-all")
  public JsonObject resetAllMetrics() {
    log.info("重置所有消费者的监控指标");

    consumerMonitor.resetAllMetrics();

    return new JsonObject()
        .put("success", true)
        .put("message", "所有监控指标重置成功");
  }

  /**
   * 获取消费者列表概览
   *
   * @return 消费者列表概览
   */
  @GetMapping("/overview")
  public JsonObject getConsumerOverview() {
    log.debug("获取消费者系统概览");

    JsonObject overview = new JsonObject();

    // 基本统计信息
    overview.put("registeredCount", consumerRegistry.getRegisteredConsumerCount());
    overview.put("activeCount", consumerRegistry.getActiveConsumerCount());
    overview.put("systemRunning", lifecycleManager.isRunning());
    overview.put("systemShuttingDown", lifecycleManager.isShuttingDown());

    // 健康状态
    JsonObject healthStatus = lifecycleManager.getHealthStatus();
    overview.put("systemStatus", healthStatus.getString("status"));
    overview.put("healthScore", healthStatus.getDouble("healthScore"));

    // 监控概览
    JsonObject metrics = consumerMonitor.getAllMetrics();
    overview.put("totalConsumers", metrics.getInteger("totalConsumers"));
    overview.put("metricsTimestamp", metrics.getString("timestamp"));

    return overview;
  }

  /**
   * 重启整个消费者系统
   *
   * @return 操作结果
   */
  @PostMapping("/system/restart")
  public JsonObject restartSystem() {
    log.info("重启消费者系统");

    try {
      // 使用Future.await在虚拟线程中同步等待
      Future.await(lifecycleManager.restart("com.vertx.template.examples.consumer"));

      return new JsonObject()
          .put("success", true)
          .put("message", "消费者系统重启成功");

    } catch (Exception e) {
      log.error("重启消费者系统失败", e);
      return new JsonObject()
          .put("success", false)
          .put("message", "消费者系统重启失败: " + e.getMessage());
    }
  }

  /**
   * 停止整个消费者系统
   *
   * @return 操作结果
   */
  @PostMapping("/system/stop")
  public JsonObject stopSystem() {
    log.info("停止消费者系统");

    try {
      // 使用Future.await在虚拟线程中同步等待
      Future.await(lifecycleManager.stop());

      return new JsonObject()
          .put("success", true)
          .put("message", "消费者系统停止成功");

    } catch (Exception e) {
      log.error("停止消费者系统失败", e);
      return new JsonObject()
          .put("success", false)
          .put("message", "消费者系统停止失败: " + e.getMessage());
    }
  }
}
