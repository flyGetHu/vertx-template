package com.vertx.template.mq.consumer.retry;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/** 死信队列处理器 负责管理死信队列和处理死信消息 */
@Slf4j
@Singleton
public class DeadLetterHandler {

  private final Vertx vertx;
  private final RetryMessagePublisher messagePublisher;
  private final Map<String, DeadLetterConfig> deadLetterConfigs = new ConcurrentHashMap<>();

  /**
   * 构造器
   *
   * @param vertx Vert.x实例
   * @param messagePublisher 消息发布器
   */
  @Inject
  public DeadLetterHandler(Vertx vertx, RetryMessagePublisher messagePublisher) {
    this.vertx = vertx;
    this.messagePublisher = messagePublisher;
  }

  /**
   * 配置消费者的死信队列
   *
   * @param consumerName 消费者名称
   * @param deadLetterConfig 死信队列配置
   */
  public void configureDeadLetter(String consumerName, DeadLetterConfig deadLetterConfig) {
    deadLetterConfigs.put(consumerName, deadLetterConfig);
    log.info("为消费者 {} 配置死信队列: {}", consumerName, deadLetterConfig.getQueueName());
  }

  /**
   * 处理死信消息
   *
   * @param consumerName 消费者名称
   * @param retryableMessage 可重试消息
   * @param finalCause 最终失败原因
   * @return 处理结果的Future
   */
  public Future<Boolean> handleDeadLetter(
      String consumerName, RetryableMessage retryableMessage, Throwable finalCause) {
    DeadLetterConfig config = deadLetterConfigs.get(consumerName);

    if (config == null) {
      // 如果没有配置死信队列，使用默认处理
      return handleDefaultDeadLetter(consumerName, retryableMessage, finalCause);
    }

    log.error(
        "消费者 {} 消息处理失败，发送到死信队列 {} - 原因: {}",
        consumerName,
        config.getQueueName(),
        finalCause.getMessage());

    // 记录死信信息
    recordDeadLetterInfo(consumerName, retryableMessage, finalCause);

    // 确保死信队列存在
    return messagePublisher
        .ensureDeadLetterQueue(config.getQueueName())
        .compose(
            v -> {
              // 发送到死信队列
              return messagePublisher.publishDeadLetterMessage(
                  retryableMessage, config.getQueueName(), finalCause);
            })
        .compose(
            v -> {
              // 执行死信处理回调
              if (config.getHandler() != null) {
                return executeDeadLetterCallback(config.getHandler(), retryableMessage, finalCause);
              }
              return Future.succeededFuture();
            })
        .map(false) // 返回false表示消息处理失败
        .onSuccess(
            result -> log.info("死信消息处理完成 - 消费者: {}, 队列: {}", consumerName, config.getQueueName()))
        .onFailure(
            cause ->
                log.error(
                    "死信消息处理失败 - 消费者: {}, 队列: {}", consumerName, config.getQueueName(), cause));
  }

  /**
   * 获取死信队列名称
   *
   * @param consumerName 消费者名称
   * @return 死信队列名称，如果未配置则返回默认名称
   */
  public String getDeadLetterQueueName(String consumerName) {
    DeadLetterConfig config = deadLetterConfigs.get(consumerName);
    if (config != null) {
      return config.getQueueName();
    }
    return getDefaultDeadLetterQueueName(consumerName);
  }

  /**
   * 检查是否配置了死信队列
   *
   * @param consumerName 消费者名称
   * @return 是否配置了死信队列
   */
  public boolean hasDeadLetterConfig(String consumerName) {
    return deadLetterConfigs.containsKey(consumerName);
  }

  /**
   * 获取死信统计信息
   *
   * @param consumerName 消费者名称
   * @return 死信统计信息
   */
  public DeadLetterStats getDeadLetterStats(String consumerName) {
    // 这里可以实现统计逻辑，比如从数据库或缓存中获取统计数据
    // 简化实现，返回空统计
    return new DeadLetterStats(consumerName, 0, LocalDateTime.now());
  }

  /**
   * 处理默认死信逻辑
   *
   * @param consumerName 消费者名称
   * @param retryableMessage 可重试消息
   * @param finalCause 最终失败原因
   * @return 处理结果的Future
   */
  private Future<Boolean> handleDefaultDeadLetter(
      String consumerName, RetryableMessage retryableMessage, Throwable finalCause) {
    String defaultQueue = getDefaultDeadLetterQueueName(consumerName);

    log.warn(
        "消费者 {} 未配置死信队列，使用默认队列 {} - 原因: {}", consumerName, defaultQueue, finalCause.getMessage());

    // 记录死信信息
    recordDeadLetterInfo(consumerName, retryableMessage, finalCause);

    // 发送到默认死信队列
    return messagePublisher
        .ensureDeadLetterQueue(defaultQueue)
        .compose(
            v ->
                messagePublisher.publishDeadLetterMessage(
                    retryableMessage, defaultQueue, finalCause))
        .map(false)
        .onSuccess(result -> log.info("默认死信消息处理完成 - 消费者: {}, 队列: {}", consumerName, defaultQueue))
        .onFailure(
            cause -> log.error("默认死信消息处理失败 - 消费者: {}, 队列: {}", consumerName, defaultQueue, cause));
  }

  /**
   * 记录死信信息
   *
   * @param consumerName 消费者名称
   * @param retryableMessage 可重试消息
   * @param finalCause 最终失败原因
   */
  private void recordDeadLetterInfo(
      String consumerName, RetryableMessage retryableMessage, Throwable finalCause) {
    JsonObject deadLetterInfo =
        new JsonObject()
            .put("consumerName", consumerName)
            .put("retryCount", retryableMessage.getRetryCount())
            .put("firstProcessTime", retryableMessage.getFirstProcessTime().toString())
            .put("lastRetryTime", retryableMessage.getLastRetryTime().toString())
            .put("failureReason", finalCause.getMessage())
            .put("failureType", finalCause.getClass().getSimpleName())
            .put("deadLetterTime", LocalDateTime.now().toString())
            .put("messageSize", retryableMessage.getBody().length)
            .put("totalFailures", retryableMessage.getFailureHistory().size());

    log.warn("死信消息记录: {}", deadLetterInfo.encode());

    // 这里可以实现持久化逻辑，比如保存到数据库或发送到监控系统
    // recordToDatabase(deadLetterInfo);
    // sendToMonitoring(deadLetterInfo);
  }

  /**
   * 执行死信处理回调
   *
   * @param handler 死信处理器
   * @param retryableMessage 可重试消息
   * @param finalCause 最终失败原因
   * @return 执行结果的Future
   */
  private Future<Void> executeDeadLetterCallback(
      DeadLetterProcessor handler, RetryableMessage retryableMessage, Throwable finalCause) {
    try {
      return handler
          .process(retryableMessage, finalCause)
          .onSuccess(v -> log.debug("死信处理回调执行成功"))
          .onFailure(cause -> log.error("死信处理回调执行失败", cause));
    } catch (Exception e) {
      log.error("执行死信处理回调时发生异常", e);
      return Future.failedFuture(e);
    }
  }

  /**
   * 获取默认死信队列名称
   *
   * @param consumerName 消费者名称
   * @return 默认死信队列名称
   */
  private String getDefaultDeadLetterQueueName(String consumerName) {
    return consumerName + ".dlq";
  }

  /** 死信队列配置类 */
  public static class DeadLetterConfig {
    private final String queueName;
    private final DeadLetterProcessor handler;
    private final boolean durable;
    private final boolean autoDelete;

    public DeadLetterConfig(String queueName) {
      this(queueName, null, true, false);
    }

    public DeadLetterConfig(String queueName, DeadLetterProcessor handler) {
      this(queueName, handler, true, false);
    }

    public DeadLetterConfig(
        String queueName, DeadLetterProcessor handler, boolean durable, boolean autoDelete) {
      this.queueName = queueName;
      this.handler = handler;
      this.durable = durable;
      this.autoDelete = autoDelete;
    }

    public String getQueueName() {
      return queueName;
    }

    public DeadLetterProcessor getHandler() {
      return handler;
    }

    public boolean isDurable() {
      return durable;
    }

    public boolean isAutoDelete() {
      return autoDelete;
    }
  }

  /** 死信统计信息类 */
  public static class DeadLetterStats {
    private final String consumerName;
    private final int totalDeadLetters;
    private final LocalDateTime lastDeadLetterTime;

    public DeadLetterStats(
        String consumerName, int totalDeadLetters, LocalDateTime lastDeadLetterTime) {
      this.consumerName = consumerName;
      this.totalDeadLetters = totalDeadLetters;
      this.lastDeadLetterTime = lastDeadLetterTime;
    }

    public String getConsumerName() {
      return consumerName;
    }

    public int getTotalDeadLetters() {
      return totalDeadLetters;
    }

    public LocalDateTime getLastDeadLetterTime() {
      return lastDeadLetterTime;
    }
  }

  /** 死信处理器接口 */
  @FunctionalInterface
  public interface DeadLetterProcessor {
    /**
     * 处理死信消息
     *
     * @param retryableMessage 可重试消息
     * @param finalCause 最终失败原因
     * @return 处理结果的Future
     */
    Future<Void> process(RetryableMessage retryableMessage, Throwable finalCause);
  }
}
