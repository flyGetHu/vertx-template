package com.vertx.template.mq.consumer;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Singleton;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 基础消费者监控器
 * 只保留核心统计功能，去除复杂的性能分析
 */
@Slf4j
@Singleton
public class BasicConsumerMonitor {

  private final Map<String, ConsumerStats> consumerStats = new ConcurrentHashMap<>();

  /**
   * 注册消费者到监控系统
   */
  public void registerConsumer(String consumerName) {
    consumerStats.putIfAbsent(consumerName, new ConsumerStats(consumerName));
    log.debug("已注册消费者到监控系统: {}", consumerName);
  }

  /**
   * 从监控系统注销消费者
   */
  public void unregisterConsumer(String consumerName) {
    consumerStats.remove(consumerName);
    log.debug("已从监控系统注销消费者: {}", consumerName);
  }

  /**
   * 记录消息处理成功
   */
  public void recordSuccess(String consumerName, long processingTimeMs) {
    ConsumerStats stats = consumerStats.get(consumerName);
    if (stats != null) {
      stats.incrementSuccess();
      stats.updateProcessingTime(processingTimeMs);
      stats.updateLastActiveTime();
    }
  }

  /**
   * 记录消息处理失败
   */
  public void recordFailure(String consumerName, long processingTimeMs) {
    ConsumerStats stats = consumerStats.get(consumerName);
    if (stats != null) {
      stats.incrementFailure();
      stats.updateProcessingTime(processingTimeMs);
      stats.updateLastActiveTime();
    }
  }

  /**
   * 记录重试
   */
  public void recordRetry(String consumerName) {
    ConsumerStats stats = consumerStats.get(consumerName);
    if (stats != null) {
      stats.incrementRetry();
    }
  }

  /**
   * 记录重试次数耗尽
   */
  public void recordRetryExhausted(String consumerName) {
    ConsumerStats stats = consumerStats.get(consumerName);
    if (stats != null) {
      stats.incrementRetryExhausted();
    }
  }

  /**
   * 获取指定消费者的统计信息
   */
  public ConsumerStats getConsumerStats(String consumerName) {
    return consumerStats.get(consumerName);
  }

  /**
   * 获取所有消费者的统计信息
   */
  public JsonObject getAllStats() {
    JsonObject result = new JsonObject();
    JsonArray consumers = new JsonArray();

    consumerStats.forEach((name, stats) -> {
      JsonObject consumerJson = new JsonObject()
          .put("name", name)
          .put("successCount", stats.getSuccessCount())
          .put("failureCount", stats.getFailureCount())
          .put("retryCount", stats.getRetryCount())
          .put("retryExhaustedCount", stats.getRetryExhaustedCount())
          .put("totalCount", stats.getTotalCount())
          .put("successRate", stats.getSuccessRate())
          .put("avgProcessingTimeMs", stats.getAvgProcessingTimeMs())
          .put("lastActiveTime", stats.getLastActiveTime() != null ? stats.getLastActiveTime().toString() : null);

      consumers.add(consumerJson);
    });

    result.put("consumers", consumers);
    result.put("totalConsumers", consumerStats.size());
    result.put("timestamp", LocalDateTime.now().toString());

    return result;
  }

  /**
   * 重置指定消费者的统计信息
   */
  public void resetStats(String consumerName) {
    ConsumerStats stats = consumerStats.get(consumerName);
    if (stats != null) {
      stats.reset();
      log.info("已重置消费者 {} 的统计信息", consumerName);
    }
  }

  /**
   * 重置所有统计信息
   */
  public void resetAllStats() {
    consumerStats.values().forEach(ConsumerStats::reset);
    log.info("已重置所有消费者统计信息");
  }

  /**
   * 简化的消费者统计信息
   */
  @Data
  public static class ConsumerStats {
    private final String consumerName;
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);
    private final AtomicLong retryCount = new AtomicLong(0);
    private final AtomicLong retryExhaustedCount = new AtomicLong(0);
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private final AtomicLong processedMessageCount = new AtomicLong(0);
    private volatile LocalDateTime lastActiveTime;

    public ConsumerStats(String consumerName) {
      this.consumerName = consumerName;
    }

    public void incrementSuccess() {
      successCount.incrementAndGet();
    }

    public void incrementFailure() {
      failureCount.incrementAndGet();
    }

    public void incrementRetry() {
      retryCount.incrementAndGet();
    }

    public void incrementRetryExhausted() {
      retryExhaustedCount.incrementAndGet();
    }

    public void updateProcessingTime(long processingTimeMs) {
      totalProcessingTime.addAndGet(processingTimeMs);
      processedMessageCount.incrementAndGet();
    }

    public void updateLastActiveTime() {
      this.lastActiveTime = LocalDateTime.now();
    }

    public long getTotalCount() {
      return successCount.get() + failureCount.get();
    }

    public double getSuccessRate() {
      long total = getTotalCount();
      return total > 0 ? (double) successCount.get() / total * 100.0 : 0.0;
    }

    public double getAvgProcessingTimeMs() {
      long count = processedMessageCount.get();
      return count > 0 ? (double) totalProcessingTime.get() / count : 0.0;
    }

    public void reset() {
      successCount.set(0);
      failureCount.set(0);
      retryCount.set(0);
      retryExhaustedCount.set(0);
      totalProcessingTime.set(0);
      processedMessageCount.set(0);
      lastActiveTime = null;
    }

    // Getter methods for AtomicLong values
    public long getSuccessCount() {
      return successCount.get();
    }

    public long getFailureCount() {
      return failureCount.get();
    }

    public long getRetryCount() {
      return retryCount.get();
    }

    public long getRetryExhaustedCount() {
      return retryExhaustedCount.get();
    }
  }
}
