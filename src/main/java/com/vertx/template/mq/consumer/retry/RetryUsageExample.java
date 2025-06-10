package com.vertx.template.mq.consumer.retry;

import com.vertx.template.mq.consumer.MessageConsumer;
import io.vertx.core.Future;
import io.vertx.rabbitmq.RabbitMQMessage;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/** 重试机制使用示例 展示如何配置和使用完善的消费者重试系统 */
@Slf4j
@Singleton
public class RetryUsageExample {

  private final RetryHandler retryHandler;
  private final DeadLetterHandler deadLetterHandler;

  @Inject
  public RetryUsageExample(RetryHandler retryHandler, DeadLetterHandler deadLetterHandler) {
    this.retryHandler = retryHandler;
    this.deadLetterHandler = deadLetterHandler;
  }

  /** 配置重试机制示例 */
  public void configureRetryExamples() {
    // 1. 配置基础重试策略
    configureBasicRetry();

    // 2. 配置高级重试策略
    configureAdvancedRetry();

    // 3. 配置死信队列
    configureDeadLetterQueue();
  }

  /** 配置基础重试策略 */
  private void configureBasicRetry() {
    String consumerName = "basic-consumer";

    // 创建重试策略：最大重试3次，初始延迟1秒，最大延迟30秒，指数倍数2.0
    RetryHandler.RetryStrategy strategy =
        RetryHandler.RetryStrategy.builder()
            .maxRetries(3)
            .initialDelay(1000)
            .maxDelay(30000)
            .multiplier(2.0)
            .build();

    retryHandler.setRetryStrategy(consumerName, strategy);
    log.info("为 {} 配置基础重试策略", consumerName);
  }

  /** 配置高级重试策略 */
  private void configureAdvancedRetry() {
    String consumerName = "advanced-consumer";

    // 创建高级重试策略：最大重试5次，初始延迟500毫秒，最大延迟5分钟，指数倍数1.5
    RetryHandler.RetryStrategy strategy =
        RetryHandler.RetryStrategy.builder()
            .maxRetries(5)
            .initialDelay(500)
            .maxDelay(300000) // 5分钟
            .multiplier(1.5)
            .build();

    retryHandler.setRetryStrategy(consumerName, strategy);
    log.info("为 {} 配置高级重试策略", consumerName);
  }

  /** 配置死信队列 */
  private void configureDeadLetterQueue() {
    String consumerName = "order-consumer";
    String deadLetterQueueName = "order.dlq";

    // 创建死信队列配置，包含自定义处理器
    DeadLetterHandler.DeadLetterConfig deadLetterConfig =
        new DeadLetterHandler.DeadLetterConfig(deadLetterQueueName, this::handleDeadLetter);

    retryHandler.configureDeadLetter(consumerName, deadLetterConfig);
    log.info("为 {} 配置死信队列: {}", consumerName, deadLetterQueueName);
  }

  /**
   * 死信消息处理示例
   *
   * @param retryableMessage 可重试消息
   * @param finalCause 最终失败原因
   * @return 处理结果的Future
   */
  private Future<Void> handleDeadLetter(RetryableMessage retryableMessage, Throwable finalCause) {
    log.error(
        "处理死信消息 - 消费者: {}, 失败原因: {}, 重试次数: {}",
        retryableMessage.getConsumerName(),
        finalCause.getMessage(),
        retryableMessage.getRetryCount());

    // 这里可以实现自定义的死信处理逻辑，比如：
    // 1. 发送告警通知
    // 2. 记录到数据库
    // 3. 发送到监控系统
    // 4. 人工处理队列

    return Future.succeededFuture();
  }

  /** 消费者实现示例 */
  @Singleton
  public static class ExampleMessageConsumer implements MessageConsumer {

    private final RetryHandler retryHandler;

    @Inject
    public ExampleMessageConsumer(RetryHandler retryHandler) {
      this.retryHandler = retryHandler;
    }

    @Override
    public String getConsumerName() {
      return "example-consumer";
    }

    @Override
    public Future<Boolean> handleMessage(RabbitMQMessage message) {
      try {
        // 模拟消息处理逻辑
        String messageBody = message.body().toString();
        log.info("处理消息: {}", messageBody);

        // 模拟随机失败（用于测试重试机制）
        if (Math.random() < 0.3) { // 30%的失败率
          throw new RuntimeException("模拟处理失败");
        }

        return Future.succeededFuture(true);

      } catch (Exception e) {
        log.error("消息处理失败", e);
        return Future.failedFuture(e);
      }
    }

    @Override
    public Future<Void> onMessageFailed(RabbitMQMessage message, Throwable cause) {
      // 当消息处理失败时，自动触发重试机制
      String queueName = getConsumerName() + ".queue";

      return retryHandler
          .handleRetry(getConsumerName(), message, cause, queueName)
          .compose(
              retryResult -> {
                if (retryResult) {
                  log.info("消息已安排重试");
                } else {
                  log.warn("消息已发送到死信队列");
                }
                return Future.succeededFuture();
              });
    }

    @Override
    public Future<Void> onStart() {
      log.info("消费者 {} 启动", getConsumerName());
      return Future.succeededFuture();
    }

    @Override
    public Future<Void> onStop() {
      log.info("消费者 {} 停止", getConsumerName());
      return Future.succeededFuture();
    }
  }

  /** 高级重试使用示例 */
  public void advancedRetryExample() {
    String consumerName = "payment-consumer";

    // 配置支付消费者的重试策略
    RetryHandler.RetryStrategy paymentRetryStrategy =
        RetryHandler.RetryStrategy.builder()
            .maxRetries(5) // 支付处理最多重试5次
            .initialDelay(2000) // 初始延迟2秒
            .maxDelay(600000) // 最大延迟10分钟
            .multiplier(2.0) // 指数倍数2.0
            .build();

    retryHandler.setRetryStrategy(consumerName, paymentRetryStrategy);

    // 配置支付死信队列
    DeadLetterHandler.DeadLetterConfig paymentDLQ =
        new DeadLetterHandler.DeadLetterConfig(
            "payment.critical.dlq",
            (retryableMessage, finalCause) -> {
              // 支付失败的关键处理
              log.error("支付消息进入死信队列，需要人工干预");
              // 发送紧急告警
              // sendCriticalAlert(retryableMessage, finalCause);
              return Future.succeededFuture();
            });

    retryHandler.configureDeadLetter(consumerName, paymentDLQ);

    log.info("支付消费者重试机制配置完成");
  }

  /** 统计信息查询示例 */
  public void queryStatsExample() {
    String consumerName = "example-consumer";

    // 查询重试统计
    RetryHandler.RetryStats retryStats = retryHandler.getRetryStats(consumerName);
    log.info(
        "重试统计 - 消费者: {}, 总重试: {}, 成功重试: {}",
        retryStats.getConsumerName(),
        retryStats.getTotalRetries(),
        retryStats.getSuccessfulRetries());

    // 查询死信统计
    DeadLetterHandler.DeadLetterStats dlqStats = deadLetterHandler.getDeadLetterStats(consumerName);
    log.info(
        "死信统计 - 消费者: {}, 死信总数: {}", dlqStats.getConsumerName(), dlqStats.getTotalDeadLetters());
  }

  /** 动态调整重试策略示例 */
  public void dynamicRetryConfigExample() {
    String consumerName = "dynamic-consumer";

    // 根据系统负载动态调整重试策略
    double systemLoad = getCurrentSystemLoad(); // 假设的方法

    RetryHandler.RetryStrategy.RetryStrategyBuilder builder = RetryHandler.RetryStrategy.builder();

    if (systemLoad > 0.8) {
      // 高负载时减少重试，增加延迟
      builder.maxRetries(2).initialDelay(5000).maxDelay(300000).multiplier(3.0);
    } else if (systemLoad > 0.5) {
      // 中等负载时标准重试
      builder.maxRetries(3).initialDelay(2000).maxDelay(60000).multiplier(2.0);
    } else {
      // 低负载时积极重试
      builder.maxRetries(5).initialDelay(1000).maxDelay(30000).multiplier(1.5);
    }

    retryHandler.setRetryStrategy(consumerName, builder.build());
    log.info("根据系统负载 {} 动态调整重试策略", systemLoad);
  }

  /**
   * 获取当前系统负载（模拟方法）
   *
   * @return 系统负载（0.0-1.0）
   */
  private double getCurrentSystemLoad() {
    // 这里应该实现真实的系统负载获取逻辑
    return Math.random();
  }
}
