package com.vertx.template.mq.consumer;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import javax.inject.Singleton;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/** 简化的消费者监控器 只保留基本的成功/失败统计 */
@Slf4j
@Singleton
public class BasicConsumerMonitor {

  private final Map<String, ConsumerStats> consumerStats = new ConcurrentHashMap<>();

  /** 注册消费者到监控系统 */
  public void registerConsumer(final String consumerName) {
    consumerStats.putIfAbsent(consumerName, new ConsumerStats(consumerName));
    log.debug("已注册消费者到监控系统: {}", consumerName);
  }

  /** 从监控系统注销消费者 */
  public void unregisterConsumer(final String consumerName) {
    consumerStats.remove(consumerName);
    log.debug("已从监控系统注销消费者: {}", consumerName);
  }

  /** 记录消息处理成功 */
  public void recordSuccess(final String consumerName, final long processingTimeMs) {
    final ConsumerStats stats = consumerStats.get(consumerName);
    if (stats != null) {
      stats.incrementSuccess();
      stats.updateLastActiveTime();
    }
  }

  /** 记录消息处理失败 */
  public void recordFailure(final String consumerName, final long processingTimeMs) {
    final ConsumerStats stats = consumerStats.get(consumerName);
    if (stats != null) {
      stats.incrementFailure();
      stats.updateLastActiveTime();
    }
  }

  /** 记录重试 */
  public void recordRetry(final String consumerName) {
    final ConsumerStats stats = consumerStats.get(consumerName);
    if (stats != null) {
      stats.incrementRetry();
    }
  }

  /** 记录重试次数耗尽 */
  public void recordRetryExhausted(final String consumerName) {
    final ConsumerStats stats = consumerStats.get(consumerName);
    if (stats != null) {
      stats.incrementRetryExhausted();
    }
  }

  /** 记录消费者重连成功 */
  public void recordReconnection(final String consumerName) {
    final ConsumerStats stats = consumerStats.get(consumerName);
    if (stats != null) {
      stats.incrementReconnection();
      stats.updateLastActiveTime();
    }
  }

  /** 记录消费者断连 */
  public void recordDisconnection(final String consumerName) {
    final ConsumerStats stats = consumerStats.get(consumerName);
    if (stats != null) {
      stats.incrementDisconnection();
    }
  }

  /** 获取指定消费者的统计信息 */
  public ConsumerStats getConsumerStats(final String consumerName) {
    return consumerStats.get(consumerName);
  }

  /** 获取所有消费者的简要统计 */
  public String getStatsString() {
    if (consumerStats.isEmpty()) {
      return "无活跃消费者";
    }

    final StringBuilder sb = new StringBuilder("消费者统计:\n");
    consumerStats.forEach(
        (name, stats) ->
            sb.append(
                String.format(
                    "  %s: 成功=%d, 失败=%d, 重试=%d, 重连=%d, 断连=%d, 成功率=%.1f%%\n",
                    name,
                    stats.getSuccessCount(),
                    stats.getFailureCount(),
                    stats.getRetryCount(),
                    stats.getReconnectionCount(),
                    stats.getDisconnectionCount(),
                    stats.getSuccessRate())));

    return sb.toString();
  }

  /** 获取活跃消费者数量 */
  public int getActiveConsumerCount() {
    return consumerStats.size();
  }

  /** 简化的消费者统计信息 */
  @Data
  public static class ConsumerStats {
    private final String consumerName;
    private final AtomicLong successCount = new AtomicLong(0);
    private final AtomicLong failureCount = new AtomicLong(0);
    private final AtomicLong retryCount = new AtomicLong(0);
    private final AtomicLong retryExhaustedCount = new AtomicLong(0);
    private final AtomicLong reconnectionCount = new AtomicLong(0);
    private final AtomicLong disconnectionCount = new AtomicLong(0);
    private volatile LocalDateTime lastActiveTime;

    public ConsumerStats(final String consumerName) {
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

    public void incrementReconnection() {
      reconnectionCount.incrementAndGet();
    }

    public void incrementDisconnection() {
      disconnectionCount.incrementAndGet();
    }

    public void updateLastActiveTime() {
      this.lastActiveTime = LocalDateTime.now();
    }

    public long getTotalCount() {
      return successCount.get() + failureCount.get();
    }

    public double getSuccessRate() {
      final long total = getTotalCount();
      return total > 0 ? (double) successCount.get() / total * 100.0 : 0.0;
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

    public long getReconnectionCount() {
      return reconnectionCount.get();
    }

    public long getDisconnectionCount() {
      return disconnectionCount.get();
    }
  }
}
