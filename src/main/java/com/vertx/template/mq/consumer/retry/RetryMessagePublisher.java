package com.vertx.template.mq.consumer.retry;

import com.rabbitmq.client.AMQP;
import com.vertx.template.mq.config.RabbitMqConfig;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.rabbitmq.RabbitMQClient;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/** 重试消息发布器 负责将重试消息重新发送到RabbitMQ队列 */
@Slf4j
@Singleton
public class RetryMessagePublisher {

  private final Vertx vertx;
  private final RabbitMqConfig config;
  private final Map<String, RabbitMQClient> publisherClients = new ConcurrentHashMap<>();

  /**
   * 构造器
   *
   * @param vertx Vert.x实例
   * @param config RabbitMQ配置
   */
  @Inject
  public RetryMessagePublisher(Vertx vertx, RabbitMqConfig config) {
    this.vertx = vertx;
    this.config = config;
  }

  /**
   * 发布重试消息
   *
   * @param retryableMessage 可重试消息
   * @param targetQueue 目标队列名称
   * @return 发布结果的Future
   */
  public Future<Void> publishRetryMessage(RetryableMessage retryableMessage, String targetQueue) {
    String publisherKey = "retry-publisher-" + targetQueue;

    return getOrCreatePublisher(publisherKey)
        .compose(
            client -> {
              // 构建消息属性
              AMQP.BasicProperties properties = buildRetryProperties(retryableMessage);

              log.debug(
                  "发布重试消息到队列 {} - 消费者: {}, 重试次数: {}",
                  targetQueue,
                  retryableMessage.getConsumerName(),
                  retryableMessage.getRetryCount());

              // 发布消息
              return client.basicPublish(
                  "", targetQueue, properties, Buffer.buffer(retryableMessage.getBody()));
            })
        .onSuccess(
            v ->
                log.debug(
                    "重试消息发布成功 - 队列: {}, 消费者: {}", targetQueue, retryableMessage.getConsumerName()))
        .onFailure(
            cause ->
                log.error(
                    "重试消息发布失败 - 队列: {}, 消费者: {}",
                    targetQueue,
                    retryableMessage.getConsumerName(),
                    cause));
  }

  /**
   * 发布死信消息
   *
   * @param retryableMessage 可重试消息
   * @param deadLetterQueue 死信队列名称
   * @param finalCause 最终失败原因
   * @return 发布结果的Future
   */
  public Future<Void> publishDeadLetterMessage(
      RetryableMessage retryableMessage, String deadLetterQueue, Throwable finalCause) {
    String publisherKey = "dlq-publisher-" + deadLetterQueue;

    return getOrCreatePublisher(publisherKey)
        .compose(
            client -> {
              // 构建死信消息属性
              AMQP.BasicProperties properties =
                  buildDeadLetterProperties(retryableMessage, finalCause);

              log.warn(
                  "发布死信消息到队列 {} - 消费者: {}, 总失败次数: {}",
                  deadLetterQueue,
                  retryableMessage.getConsumerName(),
                  retryableMessage.getFailureHistory().size());

              // 发布消息到死信队列
              return client.basicPublish(
                  "", deadLetterQueue, properties, Buffer.buffer(retryableMessage.getBody()));
            })
        .onSuccess(
            v ->
                log.info(
                    "死信消息发布成功 - 队列: {}, 消费者: {}",
                    deadLetterQueue,
                    retryableMessage.getConsumerName()))
        .onFailure(
            cause ->
                log.error(
                    "死信消息发布失败 - 队列: {}, 消费者: {}",
                    deadLetterQueue,
                    retryableMessage.getConsumerName(),
                    cause));
  }

  /**
   * 发布延迟重试消息
   *
   * @param retryableMessage 可重试消息
   * @param targetQueue 目标队列名称
   * @param delayMs 延迟时间（毫秒）
   * @return 发布结果的Future
   */
  public Future<Void> publishDelayedRetryMessage(
      RetryableMessage retryableMessage, String targetQueue, long delayMs) {
    // 使用定时器延迟发送
    return Future.future(
        promise -> {
          vertx.setTimer(
              delayMs,
              id -> {
                publishRetryMessage(retryableMessage, targetQueue).onComplete(promise);
              });
        });
  }

  /**
   * 确保死信队列存在
   *
   * @param deadLetterQueue 死信队列名称
   * @return 创建结果的Future
   */
  public Future<Void> ensureDeadLetterQueue(String deadLetterQueue) {
    String publisherKey = "dlq-setup-" + deadLetterQueue;

    return getOrCreatePublisher(publisherKey)
        .compose(
            client -> client.queueDeclare(deadLetterQueue, true, false, false, new JsonObject()))
        .map(result -> (Void) null)
        .onSuccess(v -> log.debug("死信队列已确保存在: {}", deadLetterQueue))
        .onFailure(cause -> log.error("创建死信队列失败: {}", deadLetterQueue, cause));
  }

  /**
   * 关闭所有发布器连接
   *
   * @return 关闭结果的Future
   */
  public Future<Void> closeAllPublishers() {
    log.info("关闭所有重试消息发布器...");

    Future<Void> result = Future.succeededFuture();

    for (Map.Entry<String, RabbitMQClient> entry : publisherClients.entrySet()) {
      String key = entry.getKey();
      RabbitMQClient client = entry.getValue();

      if (client != null && client.isConnected()) {
        result =
            result.compose(
                v ->
                    client
                        .stop()
                        .onSuccess(ignored -> log.debug("发布器已关闭: {}", key))
                        .onFailure(cause -> log.warn("关闭发布器失败: {}", key, cause)));
      }
    }

    return result.onComplete(
        ar -> {
          publisherClients.clear();
          log.info("所有重试消息发布器已关闭");
        });
  }

  /**
   * 获取或创建发布器客户端
   *
   * @param publisherKey 发布器键
   * @return 客户端Future
   */
  private Future<RabbitMQClient> getOrCreatePublisher(String publisherKey) {
    RabbitMQClient existingClient = publisherClients.get(publisherKey);

    if (existingClient != null && existingClient.isConnected()) {
      return Future.succeededFuture(existingClient);
    }

    // 创建新的客户端
    RabbitMQClient client = RabbitMQClient.create(vertx, config.toVertxOptions());

    return client
        .start()
        .onSuccess(
            v -> {
              publisherClients.put(publisherKey, client);
              log.debug("重试消息发布器已创建: {}", publisherKey);
            })
        .onFailure(
            cause -> {
              log.error("创建重试消息发布器失败: {}", publisherKey, cause);
              // 清理失败的客户端
              publisherClients.remove(publisherKey);
            })
        .map(client);
  }

  /**
   * 构建重试消息属性
   *
   * @param retryableMessage 可重试消息
   * @return 消息属性BasicProperties
   */
  private AMQP.BasicProperties buildRetryProperties(RetryableMessage retryableMessage) {
    Map<String, Object> retryHeaders = retryableMessage.getRetryHeaders();

    return new AMQP.BasicProperties.Builder()
        .headers(retryHeaders)
        .deliveryMode(2) // 持久化
        .contentType("application/json")
        .build();
  }

  /**
   * 构建死信消息属性
   *
   * @param retryableMessage 可重试消息
   * @param finalCause 最终失败原因
   * @return 消息属性BasicProperties
   */
  private AMQP.BasicProperties buildDeadLetterProperties(
      RetryableMessage retryableMessage, Throwable finalCause) {
    Map<String, Object> deadLetterHeaders = retryableMessage.getDeadLetterHeaders(finalCause);

    return new AMQP.BasicProperties.Builder()
        .headers(deadLetterHeaders)
        .deliveryMode(2) // 持久化
        .contentType("application/json")
        .build();
  }
}
