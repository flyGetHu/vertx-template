package com.vertx.template.mq.consumer;

import com.vertx.template.mq.config.ConsumerConfig;
import com.vertx.template.mq.config.RabbitMqConfig;
import com.vertx.template.mq.consumer.annotation.RabbitConsumer;
import com.vertx.template.mq.enums.QueueProperties;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.rabbitmq.QueueOptions;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQConsumer;
import io.vertx.rabbitmq.RabbitMQMessage;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消费者管理器
 * 为每个消费者创建独立的RabbitMQClient，避免线程安全问题
 */
@Slf4j
@Singleton
public class ConsumerManager {

  private final Vertx vertx;
  private final RabbitMqConfig config;
  private final Map<String, RabbitMQClient> consumerClients = new ConcurrentHashMap<>();
  private final Map<String, RabbitMQConsumer> activeConsumers = new ConcurrentHashMap<>();

  /**
   * 构造器
   *
   * @param vertx  Vert.x实例
   * @param config RabbitMQ配置
   */
  @Inject
  public ConsumerManager(Vertx vertx, RabbitMqConfig config) {
    this.vertx = vertx;
    this.config = config;
  }

  /**
   * 根据注解配置启动消费者
   *
   * @param messageConsumer 消息消费者实例
   * @param annotation      消费者注解
   */
  public void startConsumer(MessageConsumer messageConsumer, RabbitConsumer annotation) {
    if (!annotation.enabled()) {
      log.info("消费者 {} 已禁用，跳过启动", messageConsumer.getConsumerName());
      return;
    }

    final ConsumerConfig consumerConfig = buildConsumerConfig(annotation);
    startConsumer(messageConsumer, consumerConfig);
  }

  /**
   * 根据配置启动消费者
   *
   * @param messageConsumer 消息消费者实例
   * @param consumerConfig  消费者配置
   */
  public void startConsumer(MessageConsumer messageConsumer, ConsumerConfig consumerConfig) {
    final String consumerName = messageConsumer.getConsumerName();
    log.info("正在启动消费者: {}", consumerName);

    if (activeConsumers.containsKey(consumerName)) {
      log.warn("消费者 {} 已经在运行中", consumerName);
      return;
    }

    if (!consumerConfig.isValid()) {
      throw new IllegalArgumentException("消费者配置无效: " + consumerName);
    }

    try {
      // 为每个消费者创建独立的客户端
      final RabbitMQClient client = RabbitMQClient.create(vertx, config.toVertxOptions());
      consumerClients.put(consumerName, client);

      Future.await(client.start());
      messageConsumer.onStart(); // 调用消费者启动回调
      setupQueueAndExchange(client, consumerConfig);
      final RabbitMQConsumer consumer = createConsumer(client, messageConsumer, consumerConfig);

      activeConsumers.put(consumerName, consumer);
      log.info("消费者 {} 启动成功", consumerName);

    } catch (Exception cause) {
      log.error("启动消费者 {} 失败", consumerName, cause);
      // 清理资源
      cleanupConsumer(consumerName);
      throw new RuntimeException("启动消费者失败: " + consumerName, cause);
    }
  }

  /**
   * 停止消费者
   *
   * @param consumerName 消费者名称
   */
  public void stopConsumer(String consumerName) {
    log.info("正在停止消费者: {}", consumerName);

    final RabbitMQConsumer consumer = activeConsumers.remove(consumerName);
    final RabbitMQClient client = consumerClients.remove(consumerName);

    try {
      if (consumer != null) {
        // Vert.x RabbitMQConsumer没有显式的停止方法，
        // 通过关闭客户端来停止消费者
      }

      if (client != null && client.isConnected()) {
        Future.await(client.stop());
      }

      log.info("消费者 {} 已停止", consumerName);
    } catch (Exception cause) {
      log.error("停止消费者 {} 失败", consumerName, cause);
      throw new RuntimeException("停止消费者失败: " + consumerName, cause);
    }
  }

  /**
   * 停止所有消费者
   */
  public void stopAllConsumers() {
    log.info("正在停止所有消费者...");

    try {
      for (final String consumerName : activeConsumers.keySet()) {
        stopConsumer(consumerName);
      }

      activeConsumers.clear();
      consumerClients.clear();
      log.info("所有消费者已停止");

    } catch (Exception cause) {
      log.error("停止所有消费者失败", cause);
      throw new RuntimeException("停止所有消费者失败", cause);
    }
  }

  /**
   * 获取活跃消费者数量
   *
   * @return 活跃消费者数量
   */
  public int getActiveConsumerCount() {
    return activeConsumers.size();
  }

  /**
   * 检查消费者是否在运行
   *
   * @param consumerName 消费者名称
   * @return 是否在运行
   */
  public boolean isConsumerActive(String consumerName) {
    return activeConsumers.containsKey(consumerName);
  }

  /**
   * 设置队列和交换机
   *
   * @param client RabbitMQ客户端
   * @param config 消费者配置
   */
  private void setupQueueAndExchange(RabbitMQClient client, ConsumerConfig config) {
    try {
      // 声明交换机（如果指定了交换机）
      if (config.getExchangeName() != null && !config.getExchangeName().isEmpty()) {
        Future.await(client.exchangeDeclare(
            config.getExchangeName(),
            config.getExchangeType().getValue(),
            config.isDurable(),
            config.isAutoDelete()));
      }

      // 声明队列
      Future.await(client.queueDeclare(
          config.getQueueName(),
          config.isDurable(),
          config.isExclusive(),
          config.isAutoDelete(),
          config.getQueueArgumentsAsJson()));

      // 绑定队列到交换机（如果指定了交换机）
      if (config.getExchangeName() != null && !config.getExchangeName().isEmpty()) {
        Future.await(client.queueBind(
            config.getQueueName(),
            config.getExchangeName(),
            config.getRoutingKey() != null ? config.getRoutingKey() : ""));
      }

    } catch (Exception e) {
      throw new RuntimeException("设置队列和交换机失败", e);
    }
  }

  /**
   * 创建消费者
   *
   * @param client          RabbitMQ客户端
   * @param messageConsumer 消息处理器
   * @param config          消费者配置
   * @return 消费者实例
   */
  private RabbitMQConsumer createConsumer(RabbitMQClient client,
      MessageConsumer messageConsumer,
      ConsumerConfig config) {
    try {
      // 设置QoS
      Future.await(client.basicQos(config.getQos()));

      // 创建队列选项
      final QueueOptions options = new QueueOptions()
          .setAutoAck(config.isAutoAck())
          .setMaxInternalQueueSize(1000); // 设置内部队列大小

      // 创建消费者
      final RabbitMQConsumer consumer = Future.await(client.basicConsumer(config.getQueueName(), options));

      // 设置消息处理器
      consumer.handler(message -> handleMessage(messageConsumer, message, config.isAutoAck()));

      return consumer;
    } catch (Exception e) {
      throw new RuntimeException("创建消费者失败", e);
    }
  }

  /**
   * 处理接收到的消息
   *
   * @param messageConsumer 消息处理器
   * @param message         接收到的消息
   * @param autoAck         是否自动确认
   */
  private void handleMessage(MessageConsumer messageConsumer, RabbitMQMessage message, boolean autoAck) {
    final String consumerName = messageConsumer.getConsumerName();

    try {
      final Boolean success = messageConsumer.handleMessage(message);

      if (success && !autoAck) {
        // 手动确认消息
        final long deliveryTag = message.envelope().getDeliveryTag();
        // 注意：在Vert.x RabbitMQ客户端中，需要通过创建该消费者的客户端来确认消息
        // 这里简化处理，实际应用中需要保存客户端引用
        log.debug("消息处理成功 - 消费者: {}, deliveryTag: {}", consumerName, deliveryTag);
      }
    } catch (Exception cause) {
      log.error("消息处理失败 - 消费者: {}", consumerName, cause);
      try {
        messageConsumer.onMessageFailed(message, cause);
      } catch (Exception failCause) {
        log.error("消息失败回调执行失败 - 消费者: {}", consumerName, failCause);
      }

      if (!autoAck) {
        // 拒绝消息并重新入队
        final long deliveryTag = message.envelope().getDeliveryTag();
        log.debug("拒绝消息 - 消费者: {}, deliveryTag: {}", consumerName, deliveryTag);
      }
    }
  }

  /**
   * 根据注解构建消费者配置
   *
   * @param annotation 消费者注解
   * @return 消费者配置
   */
  private ConsumerConfig buildConsumerConfig(RabbitConsumer annotation) {
    final ConsumerConfig config = new ConsumerConfig(annotation.queue());

    if (!annotation.exchange().isEmpty()) {
      config.setExchangeName(annotation.exchange());
      config.setExchangeType(annotation.exchangeType());
    }

    if (!annotation.routingKey().isEmpty()) {
      config.setRoutingKey(annotation.routingKey());
    }

    config.setDurable(annotation.durable());
    config.setExclusive(annotation.exclusive());
    config.setAutoDelete(annotation.autoDelete());
    config.setAutoAck(annotation.autoAck());
    config.setQos(annotation.qos());
    config.setConsumerCount(annotation.consumerCount());

    // 设置扩展属性
    if (!annotation.deadLetterExchange().isEmpty()) {
      config.withDeadLetter(annotation.deadLetterExchange(), annotation.deadLetterRoutingKey());
    }

    if (annotation.messageTtl() > 0) {
      config.withMessageTtl(annotation.messageTtl());
    }

    if (annotation.maxLength() > 0) {
      config.withMaxLength(annotation.maxLength());
    }

    if (annotation.maxPriority() > 0) {
      config.withMaxPriority(annotation.maxPriority());
    }

    if (annotation.expires() > 0) {
      config.withExpires(annotation.expires());
    }

    return config;
  }

  /**
   * 清理消费者资源
   *
   * @param consumerName 消费者名称
   */
  private void cleanupConsumer(String consumerName) {
    try {
      final RabbitMQConsumer consumer = activeConsumers.remove(consumerName);
      final RabbitMQClient client = consumerClients.remove(consumerName);

      if (client != null && client.isConnected()) {
        Future.await(client.stop());
      }
    } catch (Exception e) {
      log.error("清理消费者 {} 资源失败", consumerName, e);
    }
  }
}
