package com.vertx.template.mq.consumer.retry;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.rabbitmq.RabbitMQMessage;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/** 消息重试处理器 实现指数退避重试机制和死信队列处理 */
@Slf4j
@Singleton
public class RetryHandler {

  private final Vertx vertx;
  private final RetryMessagePublisher messagePublisher;
  private final DeadLetterHandler deadLetterHandler;
  private final Map<String, RetryStrategy> retryStrategies = new ConcurrentHashMap<>();

  /** 默认重试策略 */
  private static final RetryStrategy DEFAULT_RETRY_STRATEGY =
      RetryStrategy.builder()
          .maxRetries(3)
          .initialDelay(1000)
          .maxDelay(30000)
          .multiplier(2.0)
          .build();

  /**
   * 构造器
   *
   * @param vertx Vert.x实例
   * @param messagePublisher 消息发布器
   * @param deadLetterHandler 死信队列处理器
   */
  @Inject
  public RetryHandler(
      Vertx vertx, RetryMessagePublisher messagePublisher, DeadLetterHandler deadLetterHandler) {
    this.vertx = vertx;
    this.messagePublisher = messagePublisher;
    this.deadLetterHandler = deadLetterHandler;
  }

  /**
   * 设置消费者的重试策略
   *
   * @param consumerName 消费者名称
   * @param strategy 重试策略
   */
  public void setRetryStrategy(String consumerName, RetryStrategy strategy) {
    retryStrategies.put(consumerName, strategy);
    log.info("为消费者 {} 设置重试策略: {}", consumerName, strategy);
  }

  /**
   * 处理消息重试
   *
   * @param consumerName 消费者名称
   * @param message 原始消息
   * @param cause 失败原因
   * @param queueName 目标队列名称
   * @return 重试结果的Future
   */
  public Future<Boolean> handleRetry(
      String consumerName, RabbitMQMessage message, Throwable cause, String queueName) {
    RetryStrategy strategy = retryStrategies.getOrDefault(consumerName, DEFAULT_RETRY_STRATEGY);

    // 创建可重试消息包装
    RetryableMessage retryableMessage = new RetryableMessage(message, consumerName);
    retryableMessage.recordFailure(cause);

    if (retryableMessage.hasExceededMaxRetries(strategy.getMaxRetries())) {
      log.warn("消费者 {} 消息重试次数已达上限 {}, 发送到死信队列", consumerName, strategy.getMaxRetries());
      return deadLetterHandler.handleDeadLetter(consumerName, retryableMessage, cause);
    }

    // 增加重试次数
    retryableMessage.incrementRetryCount();

    // 计算延迟时间
    long delay = calculateDelay(strategy, retryableMessage.getRetryCount() - 1);

    log.info("消费者 {} 将在 {}ms 后进行第 {} 次重试", consumerName, delay, retryableMessage.getRetryCount());

    // 延迟重试
    return messagePublisher
        .publishDelayedRetryMessage(retryableMessage, queueName, delay)
        .map(true)
        .onSuccess(
            result -> {
              log.info("消费者 {} 重试第 {} 次已安排", consumerName, retryableMessage.getRetryCount());
            })
        .onFailure(
            retryError -> {
              log.error(
                  "消费者 {} 安排重试第 {} 次失败",
                  consumerName,
                  retryableMessage.getRetryCount(),
                  retryError);
            });
  }

  /**
   * 处理消息重试（使用重试处理器）
   *
   * @param consumerName 消费者名称
   * @param message 原始消息
   * @param cause 失败原因
   * @param retryProcessor 重试处理函数
   * @return 重试结果的Future
   */
  public Future<Boolean> handleRetry(
      String consumerName,
      RabbitMQMessage message,
      Throwable cause,
      RetryProcessor retryProcessor) {
    RetryStrategy strategy = retryStrategies.getOrDefault(consumerName, DEFAULT_RETRY_STRATEGY);

    // 创建可重试消息包装
    RetryableMessage retryableMessage = new RetryableMessage(message, consumerName);
    retryableMessage.recordFailure(cause);

    if (retryableMessage.hasExceededMaxRetries(strategy.getMaxRetries())) {
      log.warn("消费者 {} 消息重试次数已达上限 {}, 发送到死信队列", consumerName, strategy.getMaxRetries());
      return deadLetterHandler.handleDeadLetter(consumerName, retryableMessage, cause);
    }

    // 增加重试次数
    retryableMessage.incrementRetryCount();

    // 计算延迟时间
    long delay = calculateDelay(strategy, retryableMessage.getRetryCount() - 1);

    log.info("消费者 {} 将在 {}ms 后进行第 {} 次重试", consumerName, delay, retryableMessage.getRetryCount());

    return scheduleRetry(delay, () -> retryProcessor.process(message))
        .onSuccess(
            result -> {
              if (result) {
                log.info("消费者 {} 重试第 {} 次成功", consumerName, retryableMessage.getRetryCount());
              } else {
                log.warn("消费者 {} 重试第 {} 次失败", consumerName, retryableMessage.getRetryCount());
              }
            })
        .onFailure(
            retryError -> {
              log.error(
                  "消费者 {} 重试第 {} 次异常", consumerName, retryableMessage.getRetryCount(), retryError);
            });
  }

  /**
   * 处理死信消息
   *
   * @param consumerName 消费者名称
   * @param message 消息
   * @param cause 失败原因
   * @return 处理结果的Future
   */
  public Future<Boolean> handleDeadLetter(
      String consumerName, RabbitMQMessage message, Throwable cause) {
    log.error("消费者 {} 处理消息失败，发送到死信队列: {}", consumerName, cause.getMessage());

    // 创建可重试消息包装
    RetryableMessage retryableMessage = new RetryableMessage(message, consumerName);
    retryableMessage.recordFailure(cause);

    return deadLetterHandler.handleDeadLetter(consumerName, retryableMessage, cause);
  }

  /**
   * 配置死信队列
   *
   * @param consumerName 消费者名称
   * @param deadLetterConfig 死信队列配置
   */
  public void configureDeadLetter(
      String consumerName, DeadLetterHandler.DeadLetterConfig deadLetterConfig) {
    deadLetterHandler.configureDeadLetter(consumerName, deadLetterConfig);
  }

  /**
   * 获取重试统计信息
   *
   * @param consumerName 消费者名称
   * @return 重试统计信息
   */
  public RetryStats getRetryStats(String consumerName) {
    // 这里可以实现统计逻辑
    return new RetryStats(consumerName, 0, 0, LocalDateTime.now());
  }

  /**
   * 计算重试延迟时间（指数退避）
   *
   * @param strategy 重试策略
   * @param retryCount 当前重试次数
   * @return 延迟时间（毫秒）
   */
  private long calculateDelay(RetryStrategy strategy, int retryCount) {
    long delay =
        (long) (strategy.getInitialDelay() * Math.pow(strategy.getMultiplier(), retryCount));
    return Math.min(delay, strategy.getMaxDelay());
  }

  /**
   * 调度重试任务
   *
   * @param delay 延迟时间
   * @param task 重试任务
   * @return 重试结果的Future
   */
  private Future<Boolean> scheduleRetry(long delay, RetryTask task) {
    return Future.future(
        promise -> {
          vertx.setTimer(
              delay,
              id -> {
                try {
                  Future<Boolean> result = task.execute();
                  result.onComplete(promise);
                } catch (Exception e) {
                  promise.fail(e);
                }
              });
        });
  }

  /** 重试策略配置类 */
  @Data
  @lombok.Builder
  public static class RetryStrategy {
    /** 最大重试次数 */
    private final int maxRetries;

    /** 初始延迟时间（毫秒） */
    private final long initialDelay;

    /** 最大延迟时间（毫秒） */
    private final long maxDelay;

    /** 延迟倍数 */
    private final double multiplier;

    @Override
    public String toString() {
      return String.format(
          "RetryStrategy{maxRetries=%d, initialDelay=%dms, maxDelay=%dms, multiplier=%.1f}",
          maxRetries, initialDelay, maxDelay, multiplier);
    }
  }

  /** 重试统计信息类 */
  public static class RetryStats {
    private final String consumerName;
    private final int totalRetries;
    private final int successfulRetries;
    private final LocalDateTime lastRetryTime;

    public RetryStats(
        String consumerName, int totalRetries, int successfulRetries, LocalDateTime lastRetryTime) {
      this.consumerName = consumerName;
      this.totalRetries = totalRetries;
      this.successfulRetries = successfulRetries;
      this.lastRetryTime = lastRetryTime;
    }

    public String getConsumerName() {
      return consumerName;
    }

    public int getTotalRetries() {
      return totalRetries;
    }

    public int getSuccessfulRetries() {
      return successfulRetries;
    }

    public LocalDateTime getLastRetryTime() {
      return lastRetryTime;
    }
  }

  /** 重试处理器接口 */
  @FunctionalInterface
  public interface RetryProcessor {
    /**
     * 处理重试消息
     *
     * @param message 消息
     * @return 处理结果的Future
     */
    Future<Boolean> process(RabbitMQMessage message);
  }

  /** 重试任务接口 */
  @FunctionalInterface
  private interface RetryTask {
    /**
     * 执行重试任务
     *
     * @return 执行结果的Future
     */
    Future<Boolean> execute();
  }
}
