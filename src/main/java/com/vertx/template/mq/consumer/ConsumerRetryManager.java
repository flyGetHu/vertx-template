package com.vertx.template.mq.consumer;

import io.vertx.core.Vertx;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 消费者重试管理器
 *
 * <p>
 * 负责管理消费者的重连策略和状态跟踪
 * <p>
 * 特性：
 * <ul>
 * <li>指数退避重试策略</li>
 * <li>抖动机制避免雷群效应</li>
 * <li>重试状态跟踪</li>
 * <li>可配置的重试限制</li>
 * </ul>
 */
@Slf4j
@Singleton
public class ConsumerRetryManager {

  private final Vertx vertx;

  // 重连配置 - 支持配置文件配置
  private final int maxRetryAttempts;
  private final long initialRetryInterval;
  private final long maxRetryInterval;
  private final double backoffMultiplier;

  // 熔断器配置
  private static final int CIRCUIT_BREAKER_FAILURE_THRESHOLD = 5; // 连续失败5次后熔断
  private static final long CIRCUIT_BREAKER_TIMEOUT = 300000; // 熔断5分钟

  // 消费者重试状态存储
  private final Map<String, ConsumerRetryState> retryStates = new ConcurrentHashMap<>();
  private final Map<String, Long> retryTimers = new ConcurrentHashMap<>();
  private final Map<String, CircuitBreakerState> circuitBreakers = new ConcurrentHashMap<>();

  @Inject
  public ConsumerRetryManager(final Vertx vertx) {
    this.vertx = vertx;
    // TODO: 从配置文件读取，暂时使用默认值
    this.maxRetryAttempts = 15;
    this.initialRetryInterval = 1000;
    this.maxRetryInterval = 60000;
    this.backoffMultiplier = 1.5;
  }

  /**
   * 注册消费者到重试管理器
   */
  public void registerConsumer(final String consumerName) {
    retryStates.putIfAbsent(consumerName, new ConsumerRetryState(consumerName));
    circuitBreakers.putIfAbsent(consumerName, new CircuitBreakerState(consumerName));
    log.debug("消费者已注册到重试管理器: {}", consumerName);
  }

  /**
   * 注销消费者
   */
  public void unregisterConsumer(final String consumerName) {
    // 取消正在进行的重试任务
    cancelRetryTimer(consumerName);
    retryStates.remove(consumerName);
    circuitBreakers.remove(consumerName);
    log.debug("消费者已从重试管理器注销: {}", consumerName);
  }

  /**
   * 记录消费者重连成功
   */
  public void recordSuccess(final String consumerName) {
    final ConsumerRetryState state = retryStates.get(consumerName);
    final CircuitBreakerState circuitBreaker = circuitBreakers.get(consumerName);

    if (state != null) {
      state.reset();
      log.debug("消费者 {} 重连成功，重试状态已重置", consumerName);
    }

    if (circuitBreaker != null) {
      circuitBreaker.recordSuccess();
      log.debug("消费者 {} 熔断器已重置", consumerName);
    }
  }

  /**
   * 调度消费者重试
   *
   * @param consumerName 消费者名称
   * @param retryAction  重试执行的动作
   * @return 是否成功调度重试
   */
  public boolean scheduleRetry(final String consumerName, final Runnable retryAction) {
    final ConsumerRetryState state = retryStates.get(consumerName);
    final CircuitBreakerState circuitBreaker = circuitBreakers.get(consumerName);

    if (state == null || circuitBreaker == null) {
      log.warn("未找到消费者 {} 的重试状态或熔断器", consumerName);
      return false;
    }

    // 使用CAS操作尝试开始重试，避免重复调度（无锁并发控制）
    if (!state.tryStartRetry()) {
      log.debug("消费者 {} 已在重试中，跳过重复调度", consumerName);
      return false;
    }

    try {
      // 检查熔断器状态
      if (circuitBreaker.circuitOpen) {
        if (!circuitBreaker.canAttemptReset(CIRCUIT_BREAKER_TIMEOUT)) {
          log.debug("消费者 {} 熔断器处于开启状态，跳过重试", consumerName);
          return false;
        } else {
          log.info("消费者 {} 熔断器超时，尝试半开状态", consumerName);
        }
      }

      // 检查是否已达到最大重试次数
      if (state.getRetryCount() >= maxRetryAttempts) {
        log.error("消费者 {} 已达到最大重试次数 {}，暂停重试", consumerName, maxRetryAttempts);
        state.markAsStopped();
        return false;
      }

      // 取消之前的重试任务
      cancelRetryTimer(consumerName);

      // 计算重试间隔
      final long retryInterval = calculateRetryInterval(state.getRetryCount());
      state.incrementRetryCount();
      state.updateLastRetryTime();

      log.info("消费者 {} 将在 {}ms 后进行第 {} 次重连尝试",
          consumerName, retryInterval, state.getRetryCount());

      // 调度重试任务
      final Long timerId = vertx.setTimer(retryInterval, id -> {
        retryTimers.remove(consumerName);
        try {
          retryAction.run();
          // 重试成功，在recordSuccess中会重置熔断器
        } catch (Exception e) {
          log.error("消费者 {} 重试执行失败", consumerName, e);

          // 记录熔断器失败
          circuitBreaker.recordFailure();

          // 检查是否需要开启熔断器
          if (circuitBreaker.shouldTripCircuit(CIRCUIT_BREAKER_FAILURE_THRESHOLD)) {
            circuitBreaker.openCircuit();
            log.warn("消费者 {} 连续失败 {} 次，熔断器开启",
                consumerName, CIRCUIT_BREAKER_FAILURE_THRESHOLD);
            state.markAsStopped();
            return;
          }

          // 避免递归调用，使用异步方式重新调度
          if (state.getRetryCount() < maxRetryAttempts) {
            vertx.runOnContext(v -> scheduleRetry(consumerName, retryAction));
          } else {
            log.error("消费者 {} 重试次数已耗尽，停止重试", consumerName);
            state.markAsStopped();
          }
        } finally {
          // 无论成功失败，都要结束重试状态
          state.endRetry();
        }
      });

      retryTimers.put(consumerName, timerId);
      return true;

    } finally {
      // 如果调度失败，需要重置重试状态
      if (retryTimers.get(consumerName) == null) {
        state.endRetry();
      }
    }
  }

  /**
   * 取消消费者的重试任务
   */
  public void cancelRetry(final String consumerName) {
    cancelRetryTimer(consumerName);
    final ConsumerRetryState state = retryStates.get(consumerName);
    if (state != null) {
      state.reset();
      log.debug("消费者 {} 的重试任务已取消", consumerName);
    }
  }

  /**
   * 检查消费者是否正在重试中
   */
  public boolean isRetrying(final String consumerName) {
    final ConsumerRetryState state = retryStates.get(consumerName);
    return state != null && (state.isRetryInProgress() || retryTimers.containsKey(consumerName));
  }

  /**
   * 检查消费者是否已停止重试
   */
  public boolean isStopped(final String consumerName) {
    final ConsumerRetryState state = retryStates.get(consumerName);
    return state != null && state.isStopped();
  }

  /**
   * 获取消费者重试状态
   */
  public ConsumerRetryState getRetryState(final String consumerName) {
    return retryStates.get(consumerName);
  }

  /**
   * 手动重置消费者重试状态（用于恢复停止的消费者）
   */
  public void resetConsumer(final String consumerName) {
    final ConsumerRetryState state = retryStates.get(consumerName);
    final CircuitBreakerState circuitBreaker = circuitBreakers.get(consumerName);

    if (state != null) {
      cancelRetryTimer(consumerName);
      state.reset();
      log.info("消费者 {} 的重试状态已手动重置", consumerName);
    }

    if (circuitBreaker != null) {
      circuitBreaker.recordSuccess(); // 这会重置熔断器
      log.info("消费者 {} 的熔断器已手动重置", consumerName);
    }
  }

  /**
   * 手动重置消费者熔断器
   */
  public void resetCircuitBreaker(final String consumerName) {
    final CircuitBreakerState circuitBreaker = circuitBreakers.get(consumerName);
    if (circuitBreaker != null) {
      circuitBreaker.recordSuccess();
      log.info("消费者 {} 熔断器已手动重置", consumerName);
    } else {
      log.warn("未找到消费者 {} 的熔断器", consumerName);
    }
  }

  /**
   * 获取消费者熔断器状态
   */
  public CircuitBreakerState getCircuitBreakerState(final String consumerName) {
    return circuitBreakers.get(consumerName);
  }

  /**
   * 获取所有消费者的重试状态摘要
   */
  public String getRetryStatusSummary() {
    if (retryStates.isEmpty()) {
      return "无消费者注册到重试管理器";
    }

    final StringBuilder sb = new StringBuilder("消费者重试状态:\n");
    retryStates.forEach((name, state) -> {
      final CircuitBreakerState circuitBreaker = circuitBreakers.get(name);

      final String status;
      if (state.isStopped()) {
        status = "已停止";
      } else if (isRetrying(name)) {
        status = "重试中";
      } else if (circuitBreaker != null && circuitBreaker.circuitOpen) {
        status = "熔断中";
      } else {
        status = "正常";
      }

      sb.append(String.format("  %s: %s (重试次数: %d", name, status, state.getRetryCount()));

      if (circuitBreaker != null) {
        sb.append(String.format(", 连续失败: %d", circuitBreaker.getConsecutiveFailures()));
        if (circuitBreaker.circuitOpen && circuitBreaker.circuitOpenTime != null) {
          sb.append(String.format(", 熔断开始: %s", circuitBreaker.circuitOpenTime));
        }
      }

      sb.append(")\n");
    });

    return sb.toString();
  }

  // ================================
  // 私有方法
  // ================================

  /**
   * 计算重试间隔（指数退避 + 抖动）
   */
  private long calculateRetryInterval(final int retryAttempt) {
    // 基础间隔 = 初始间隔 * (退避倍数 ^ 重试次数)
    double baseInterval = initialRetryInterval * Math.pow(backoffMultiplier, retryAttempt);

    // 限制最大间隔
    baseInterval = Math.min(baseInterval, maxRetryInterval);

    // 添加抖动（±20%的随机波动）
    final double jitterFactor = 0.8 + (Math.random() * 0.4); // 0.8 - 1.2
    final long finalInterval = (long) (baseInterval * jitterFactor);

    return Math.max(finalInterval, initialRetryInterval);
  }

  /**
   * 取消重试定时器
   */
  private void cancelRetryTimer(final String consumerName) {
    final Long timerId = retryTimers.remove(consumerName);
    if (timerId != null) {
      vertx.cancelTimer(timerId);
    }
  }

  /**
   * 消费者重试状态
   */
  @Data
  public static class ConsumerRetryState {
    private final String consumerName;
    private final AtomicInteger retryCount = new AtomicInteger(0);
    private volatile LocalDateTime lastRetryTime;
    private final AtomicBoolean stopped = new AtomicBoolean(false);
    private final AtomicBoolean retryInProgress = new AtomicBoolean(false);

    public ConsumerRetryState(final String consumerName) {
      this.consumerName = consumerName;
    }

    public int getRetryCount() {
      return retryCount.get();
    }

    public void incrementRetryCount() {
      retryCount.incrementAndGet();
    }

    public void updateLastRetryTime() {
      this.lastRetryTime = LocalDateTime.now();
    }

    public void reset() {
      retryCount.set(0);
      this.lastRetryTime = null;
      stopped.set(false);
      retryInProgress.set(false);
    }

    public void markAsStopped() {
      stopped.set(true);
    }

    public boolean isStopped() {
      return stopped.get();
    }

    /**
     * 尝试开始重试（CAS操作，线程安全）
     */
    public boolean tryStartRetry() {
      return retryInProgress.compareAndSet(false, true);
    }

    /**
     * 结束重试
     */
    public void endRetry() {
      retryInProgress.set(false);
    }

    /**
     * 检查是否正在重试中
     */
    public boolean isRetryInProgress() {
      return retryInProgress.get();
    }
  }

  /**
   * 熔断器状态
   */
  @Data
  public static class CircuitBreakerState {
    private final String consumerName;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile LocalDateTime circuitOpenTime;
    private volatile boolean circuitOpen = false;

    public CircuitBreakerState(final String consumerName) {
      this.consumerName = consumerName;
    }

    public void recordFailure() {
      consecutiveFailures.incrementAndGet();
    }

    public void recordSuccess() {
      consecutiveFailures.set(0);
      circuitOpen = false;
      circuitOpenTime = null;
    }

    public void openCircuit() {
      circuitOpen = true;
      circuitOpenTime = LocalDateTime.now();
    }

    public boolean shouldTripCircuit(final int threshold) {
      return consecutiveFailures.get() >= threshold;
    }

    public boolean canAttemptReset(final long timeoutMs) {
      if (!circuitOpen || circuitOpenTime == null) {
        return true;
      }
      return LocalDateTime.now().isAfter(circuitOpenTime.plusNanos(timeoutMs * 1_000_000));
    }

    public int getConsecutiveFailures() {
      return consecutiveFailures.get();
    }
  }
}
