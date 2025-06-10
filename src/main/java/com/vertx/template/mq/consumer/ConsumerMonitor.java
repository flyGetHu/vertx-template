package com.vertx.template.mq.consumer;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/** 消费者监控组件 负责监控消费者的健康状态、性能指标和异常统计 */
@Slf4j
@Singleton
public class ConsumerMonitor {

  private final Vertx vertx;
  private final Map<String, ConsumerMetrics> consumerMetrics = new ConcurrentHashMap<>();
  private volatile long monitorTimerId = -1;
  private volatile boolean monitoring = false;

  /**
   * 构造器
   *
   * @param vertx Vert.x实例
   */
  @Inject
  public ConsumerMonitor(Vertx vertx) {
    this.vertx = vertx;
  }

  /**
   * 启动监控
   *
   * @param monitorIntervalMs 监控间隔（毫秒）
   * @return 启动结果的Future
   */
  public Future<Void> startMonitoring(long monitorIntervalMs) {
    if (monitoring) {
      log.warn("监控已经在运行中");
      return Future.succeededFuture();
    }

    log.info("启动消费者监控，间隔: {}ms", monitorIntervalMs);

    monitorTimerId =
        vertx.setPeriodic(
            monitorIntervalMs,
            id -> {
              try {
                performHealthCheck();
                logMetrics();
              } catch (Exception e) {
                log.error("监控检查失败", e);
              }
            });

    monitoring = true;
    return Future.succeededFuture();
  }

  /**
   * 停止监控
   *
   * @return 停止结果的Future
   */
  public Future<Void> stopMonitoring() {
    if (!monitoring) {
      log.warn("监控未运行");
      return Future.succeededFuture();
    }

    log.info("停止消费者监控");

    if (monitorTimerId != -1) {
      vertx.cancelTimer(monitorTimerId);
      monitorTimerId = -1;
    }

    monitoring = false;
    return Future.succeededFuture();
  }

  /**
   * 记录消息处理成功
   *
   * @param consumerName 消费者名称
   * @param processingTime 处理时间（毫秒）
   */
  public void recordMessageSuccess(String consumerName, long processingTime) {
    ConsumerMetrics metrics = getOrCreateMetrics(consumerName);
    metrics.incrementSuccessCount();
    metrics.updateProcessingTime(processingTime);
    metrics.updateLastSuccessTime();
  }

  /**
   * 记录消息处理失败
   *
   * @param consumerName 消费者名称
   * @param cause 失败原因
   * @param processingTime 处理时间（毫秒）
   */
  public void recordMessageFailure(String consumerName, Throwable cause, long processingTime) {
    ConsumerMetrics metrics = getOrCreateMetrics(consumerName);
    metrics.incrementFailureCount();
    metrics.updateProcessingTime(processingTime);
    metrics.updateLastFailureTime();
    metrics.addFailure(cause);
  }

  /**
   * 记录消息重试
   *
   * @param consumerName 消费者名称
   * @param retryCount 重试次数
   */
  public void recordMessageRetry(String consumerName, int retryCount) {
    ConsumerMetrics metrics = getOrCreateMetrics(consumerName);
    metrics.incrementRetryCount();
    metrics.updateMaxRetryCount(retryCount);
  }

  /**
   * 获取消费者指标
   *
   * @param consumerName 消费者名称
   * @return 消费者指标
   */
  public ConsumerMetrics getConsumerMetrics(String consumerName) {
    return consumerMetrics.get(consumerName);
  }

  /**
   * 获取所有消费者指标
   *
   * @return 所有消费者指标的JSON表示
   */
  public JsonObject getAllMetrics() {
    JsonObject result = new JsonObject();
    JsonArray consumers = new JsonArray();

    consumerMetrics.forEach(
        (name, metrics) -> {
          JsonObject consumerMetrics =
              new JsonObject()
                  .put("name", name)
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
                  .put("recentFailures", JsonObject.mapFrom(metrics.getRecentFailures()));

          consumers.add(consumerMetrics);
        });

    result.put("consumers", consumers);
    result.put("totalConsumers", consumerMetrics.size());
    result.put("timestamp", LocalDateTime.now().toString());

    return result;
  }

  /**
   * 重置消费者指标
   *
   * @param consumerName 消费者名称
   */
  public void resetMetrics(String consumerName) {
    consumerMetrics.remove(consumerName);
    log.info("已重置消费者 {} 的指标", consumerName);
  }

  /** 重置所有指标 */
  public void resetAllMetrics() {
    consumerMetrics.clear();
    log.info("已重置所有消费者指标");
  }

  /**
   * 检查消费者健康状态
   *
   * @param consumerName 消费者名称
   * @return 健康状态检查结果
   */
  public JsonObject checkConsumerHealth(String consumerName) {
    ConsumerMetrics metrics = consumerMetrics.get(consumerName);
    if (metrics == null) {
      return new JsonObject()
          .put("name", consumerName)
          .put("status", "UNKNOWN")
          .put("message", "未找到消费者指标");
    }

    JsonObject health = new JsonObject().put("name", consumerName);

    // 检查成功率
    double successRate = metrics.getSuccessRate();
    if (successRate >= 0.95) {
      health.put("status", "HEALTHY");
    } else if (successRate >= 0.8) {
      health.put("status", "WARNING");
    } else {
      health.put("status", "UNHEALTHY");
    }

    // 检查最近失败时间
    LocalDateTime lastFailure = metrics.getLastFailureTime();
    LocalDateTime now = LocalDateTime.now();
    if (lastFailure != null && lastFailure.isAfter(now.minusMinutes(5))) {
      health.put("recentFailures", true);
    }

    // 检查平均处理时间
    double avgProcessingTime = metrics.getAvgProcessingTime();
    if (avgProcessingTime > 5000) { // 超过5秒认为处理慢
      health.put("slowProcessing", true);
    }

    health
        .put("successRate", successRate)
        .put("avgProcessingTime", avgProcessingTime)
        .put("totalMessages", metrics.getTotalCount())
        .put("recentFailures", metrics.getRecentFailures());

    return health;
  }

  /**
   * 获取或创建消费者指标
   *
   * @param consumerName 消费者名称
   * @return 消费者指标
   */
  private ConsumerMetrics getOrCreateMetrics(String consumerName) {
    return consumerMetrics.computeIfAbsent(consumerName, k -> new ConsumerMetrics(consumerName));
  }

  /** 执行健康检查 */
  private void performHealthCheck() {
    consumerMetrics.forEach(
        (name, metrics) -> {
          JsonObject health = checkConsumerHealth(name);
          String status = health.getString("status");

          if ("UNHEALTHY".equals(status)) {
            log.warn("消费者 {} 健康状态异常: {}", name, health.encode());
          } else if ("WARNING".equals(status)) {
            log.warn("消费者 {} 健康状态警告: {}", name, health.encode());
          }
        });
  }

  /** 记录指标日志 */
  private void logMetrics() {
    if (log.isDebugEnabled()) {
      consumerMetrics.forEach(
          (name, metrics) -> {
            log.debug(
                "消费者 {} 指标 - 成功: {}, 失败: {}, 重试: {}, 成功率: {:.2f}%, 平均处理时间: {:.2f}ms",
                name,
                metrics.getSuccessCount(),
                metrics.getFailureCount(),
                metrics.getRetryCount(),
                metrics.getSuccessRate() * 100,
                metrics.getAvgProcessingTime());
          });
    }
  }

  /** 消费者指标数据类 */
  @Data
  public static class ConsumerMetrics {
    private final String consumerName;
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);
    private final AtomicLong retryCount = new AtomicLong(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private volatile long maxProcessingTime = 0;
    private volatile long minProcessingTime = Long.MAX_VALUE;
    private volatile int maxRetryCount = 0;
    private volatile LocalDateTime lastSuccessTime;
    private volatile LocalDateTime lastFailureTime;
    private final Map<String, Integer> recentFailures = new ConcurrentHashMap<>();

    public ConsumerMetrics(String consumerName) {
      this.consumerName = consumerName;
    }

    public void incrementSuccessCount() {
      successCount.incrementAndGet();
    }

    public void incrementFailureCount() {
      failureCount.incrementAndGet();
    }

    public void incrementRetryCount() {
      retryCount.incrementAndGet();
    }

    public void updateProcessingTime(long processingTime) {
      totalProcessingTime.addAndGet(processingTime);

      synchronized (this) {
        if (processingTime > maxProcessingTime) {
          maxProcessingTime = processingTime;
        }
        if (processingTime < minProcessingTime) {
          minProcessingTime = processingTime;
        }
      }
    }

    public void updateMaxRetryCount(int retryCount) {
      synchronized (this) {
        if (retryCount > maxRetryCount) {
          maxRetryCount = retryCount;
        }
      }
    }

    public void updateLastSuccessTime() {
      lastSuccessTime = LocalDateTime.now();
    }

    public void updateLastFailureTime() {
      lastFailureTime = LocalDateTime.now();
    }

    public void addFailure(Throwable cause) {
      String errorType = cause.getClass().getSimpleName();
      recentFailures.merge(errorType, 1, Integer::sum);

      // 只保留最近的错误类型（最多10种）
      if (recentFailures.size() > 10) {
        recentFailures.entrySet().removeIf(entry -> entry.getValue() == 1);
      }
    }

    public long getTotalCount() {
      return successCount.get() + failureCount.get();
    }

    public double getSuccessRate() {
      long total = getTotalCount();
      return total == 0 ? 1.0 : (double) successCount.get() / total;
    }

    public double getAvgProcessingTime() {
      long total = getTotalCount();
      return total == 0 ? 0.0 : (double) totalProcessingTime.get() / total;
    }

    public long getSuccessCount() {
      return successCount.get();
    }

    public long getFailureCount() {
      return failureCount.get();
    }

    public long getRetryCount() {
      return retryCount.get();
    }

    public Map<String, Integer> getRecentFailures() {
      return new ConcurrentHashMap<>(recentFailures);
    }
  }
}
