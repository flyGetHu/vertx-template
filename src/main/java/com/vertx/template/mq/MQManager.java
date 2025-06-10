package com.vertx.template.mq;

import com.google.inject.Injector;
import com.vertx.template.mq.config.RabbitMqConfig;
import com.vertx.template.mq.connection.ChannelPool;
import com.vertx.template.mq.consumer.BasicConsumerMonitor;
import com.vertx.template.mq.consumer.MessageConsumer;
import com.vertx.template.mq.consumer.RabbitConsumer;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.rabbitmq.QueueOptions;
import io.vertx.rabbitmq.RabbitMQClient;
import io.vertx.rabbitmq.RabbitMQConsumer;
import io.vertx.rabbitmq.RabbitMQMessage;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;

/**
 * 简化的MQ管理器
 * 统一管理消息的生产和消费功能
 */
@Slf4j
@Singleton
public class MQManager {

  // 消费者相关字段
  private final Vertx vertx;
  private final RabbitMqConfig config;
  private final Injector injector;
  private final BasicConsumerMonitor monitor;

  // 生产者相关字段
  private final ChannelPool channelPool;

  // 消费者存储
  private final Map<String, RabbitMQClient> consumerClients = new ConcurrentHashMap<>();
  private final Map<String, RabbitMQConsumer> activeConsumers = new ConcurrentHashMap<>();
  private final Map<String, MessageConsumer> registeredConsumers = new ConcurrentHashMap<>();
  private final Map<String, RabbitConsumer> consumerAnnotations = new ConcurrentHashMap<>();

  @Inject
  public MQManager(
      final Vertx vertx,
      final RabbitMqConfig config,
      final Injector injector,
      final BasicConsumerMonitor monitor,
      final ChannelPool channelPool) {
    this.vertx = vertx;
    this.config = config;
    this.injector = injector;
    this.monitor = monitor;
    this.channelPool = channelPool;
  }

  // ========================================
  // 消费者管理功能
  // ========================================

  /**
   * 自动扫描并启动所有消费者
   */
  public void scanAndStartConsumers(final String basePackage) {
    log.info("开始扫描并启动消费者，基础包: {}", basePackage);

    try {
      final Reflections reflections = new Reflections(basePackage);
      final Set<Class<?>> consumerClasses = reflections.getTypesAnnotatedWith(RabbitConsumer.class);

      if (consumerClasses.isEmpty()) {
        log.info("未找到消费者类，包路径: {}", basePackage);
        return;
      }

      log.info("找到 {} 个消费者类", consumerClasses.size());

      for (final Class<?> consumerClass : consumerClasses) {
        registerAndStartConsumer(consumerClass);
      }

      log.info("消费者扫描和启动完成，共启动 {} 个消费者", activeConsumers.size());
    } catch (Exception e) {
      log.error("扫描消费者失败", e);
      throw new RuntimeException("扫描消费者失败", e);
    }
  }

  /**
   * 注册并启动单个消费者
   */
  public void registerAndStartConsumer(final Class<?> consumerClass) {
    try {
      if (!MessageConsumer.class.isAssignableFrom(consumerClass)) {
        log.warn("类 {} 未实现 MessageConsumer 接口，跳过", consumerClass.getName());
        return;
      }

      final RabbitConsumer annotation = consumerClass.getAnnotation(RabbitConsumer.class);
      if (annotation == null) {
        log.warn("类 {} 未标注 @RabbitConsumer 注解，跳过", consumerClass.getName());
        return;
      }

      // 通过Guice创建实例
      final MessageConsumer consumer = (MessageConsumer) injector.getInstance(consumerClass);
      final String consumerName = consumer.getConsumerName();

      if (registeredConsumers.containsKey(consumerName)) {
        log.warn("消费者 {} 已存在，跳过重复注册", consumerName);
        return;
      }

      // 注册消费者
      registeredConsumers.put(consumerName, consumer);
      consumerAnnotations.put(consumerName, annotation);

      log.info("注册消费者成功: {} -> {}", consumerName, consumerClass.getName());

      // 如果已启用，立即启动
      if (annotation.enabled()) {
        startConsumer(consumerName);
      } else {
        log.info("消费者 {} 已禁用，不会自动启动", consumerName);
      }

    } catch (Exception e) {
      log.error("注册消费者失败: {}", consumerClass.getName(), e);
      throw new RuntimeException("注册消费者失败: " + consumerClass.getName(), e);
    }
  }

  /**
   * 启动指定消费者
   */
  public void startConsumer(final String consumerName) {
    final MessageConsumer consumer = registeredConsumers.get(consumerName);
    final RabbitConsumer annotation = consumerAnnotations.get(consumerName);

    if (consumer == null || annotation == null) {
      throw new IllegalArgumentException("消费者未注册: " + consumerName);
    }

    if (activeConsumers.containsKey(consumerName)) {
      log.warn("消费者 {} 已经在运行中", consumerName);
      return;
    }

    log.info("正在启动消费者: {} - 队列: {}", consumerName, annotation.queueName());

    try {
      // 为每个消费者创建独立的客户端
      final RabbitMQClient client = RabbitMQClient.create(vertx, config.toVertxOptions());
      consumerClients.put(consumerName, client);

      Future.await(client.start());
      consumer.onStart(); // 调用启动回调

      // 创建消费者（队列必须预先存在）
      final QueueOptions options = new QueueOptions()
          .setAutoAck(annotation.autoAck())
          .setMaxInternalQueueSize(1000);

      final RabbitMQConsumer rabbitConsumer = Future.await(client.basicConsumer(annotation.queueName(), options));

      // 设置消息处理器
      rabbitConsumer.handler(message -> handleMessage(consumer, annotation, message));

      activeConsumers.put(consumerName, rabbitConsumer);
      monitor.registerConsumer(consumerName);

      log.info("消费者 {} 启动成功 - 队列: {}", consumerName, annotation.queueName());

    } catch (Exception cause) {
      log.error("启动消费者 {} 失败 - 请确保队列 {} 已存在", consumerName, annotation.queueName(), cause);
      cleanupConsumer(consumerName);
      throw new RuntimeException("启动消费者失败: " + consumerName + "，请确保队列已存在", cause);
    }
  }

  /**
   * 停止指定消费者
   */
  public void stopConsumer(final String consumerName) {
    log.info("正在停止消费者: {}", consumerName);

    final MessageConsumer consumer = registeredConsumers.get(consumerName);
    if (consumer != null) {
      try {
        consumer.onStop();
      } catch (Exception e) {
        log.error("消费者 {} 停止回调失败", consumerName, e);
      }
    }

    final RabbitMQConsumer rabbitConsumer = activeConsumers.remove(consumerName);
    final RabbitMQClient client = consumerClients.remove(consumerName);

    try {
      if (client != null && client.isConnected()) {
        Future.await(client.stop());
      }
      monitor.unregisterConsumer(consumerName);
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
      registeredConsumers.clear();
      consumerAnnotations.clear();

      log.info("所有消费者已停止");

    } catch (Exception cause) {
      log.error("停止所有消费者失败", cause);
      throw new RuntimeException("停止所有消费者失败", cause);
    }
  }

  // ========================================
  // 消息生产功能
  // ========================================

  /**
   * 发送简单消息到队列
   */
  public void sendToQueue(final String queueName, final String message) {
    sendToQueue(queueName, message, null);
  }

  /**
   * 发送消息到队列（带属性）
   */
  public void sendToQueue(final String queueName, final String message, final JsonObject properties) {
    final RabbitMQClient client = channelPool.borrowClient();
    try {
      // 直接发送消息，不创建队列
      Future.await(client.basicPublish("", queueName, Buffer.buffer(message)));
      log.debug("消息发送成功 - 队列: {}, 消息: {}", queueName, message);
    } catch (Exception cause) {
      log.error("发送消息到队列失败 - 队列: {}, 消息: {} - 请确保队列已存在", queueName, message, cause);
      throw new RuntimeException("发送消息到队列失败，请确保队列 [" + queueName + "] 已存在", cause);
    } finally {
      channelPool.returnClient(client);
    }
  }

  /**
   * 发送消息到交换机
   */
  public void sendToExchange(final String exchangeName, final String routingKey, final String message) {
    sendToExchange(exchangeName, routingKey, message, null);
  }

  /**
   * 发送消息到交换机（带属性）
   */
  public void sendToExchange(final String exchangeName, final String routingKey, final String message,
      final JsonObject properties) {
    final RabbitMQClient client = channelPool.borrowClient();
    try {
      // 直接发送消息，不创建交换机
      Future.await(client.basicPublish(exchangeName, routingKey, Buffer.buffer(message)));
      log.debug("消息发送成功 - 交换机: {}, 路由键: {}, 消息: {}", exchangeName, routingKey, message);
    } catch (Exception cause) {
      log.error("发送消息到交换机失败 - 交换机: {}, 路由键: {}, 消息: {} - 请确保交换机已存在",
          exchangeName, routingKey, message, cause);
      throw new RuntimeException("发送消息到交换机失败，请确保交换机 [" + exchangeName + "] 已存在", cause);
    } finally {
      channelPool.returnClient(client);
    }
  }

  /**
   * 发送 JSON 消息到队列
   */
  public void sendJsonToQueue(final String queueName, final JsonObject jsonData) {
    sendToQueue(queueName, jsonData.encode(), createJsonProperties());
  }

  /**
   * 发送 JSON 消息到交换机
   */
  public void sendJsonToExchange(final String exchangeName, final String routingKey, final JsonObject jsonData) {
    sendToExchange(exchangeName, routingKey, jsonData.encode(), createJsonProperties());
  }

  // ========================================
  // 内部辅助方法
  // ========================================

  /**
   * 处理接收到的消息
   */
  private void handleMessage(final MessageConsumer consumer, final RabbitConsumer annotation,
      final RabbitMQMessage message) {
    final String consumerName = consumer.getConsumerName();
    final long startTime = System.currentTimeMillis();

    try {
      final Boolean result = consumer.handleMessage(message);
      final long processingTime = System.currentTimeMillis() - startTime;

      if (result != null && result) {
        monitor.recordSuccess(consumerName, processingTime);
        log.debug("消息处理成功 - 消费者: {}", consumerName);
      } else {
        monitor.recordFailure(consumerName, processingTime);
        log.warn("消息处理失败 - 消费者: {}", consumerName);

        // 简单重试机制
        handleRetry(consumer, annotation, message, "处理返回false");
      }

    } catch (Exception cause) {
      final long processingTime = System.currentTimeMillis() - startTime;
      monitor.recordFailure(consumerName, processingTime);
      log.error("处理消息时发生异常 - 消费者: {}", consumerName, cause);

      try {
        consumer.onMessageFailed(message, cause);
      } catch (Exception callbackException) {
        log.error("消息失败回调执行异常", callbackException);
      }

      // 简单重试机制
      handleRetry(consumer, annotation, message, cause.getMessage());
    }
  }

  /**
   * 简单的重试处理
   */
  private void handleRetry(final MessageConsumer consumer, final RabbitConsumer annotation,
      final RabbitMQMessage message, final String errorReason) {
    final String consumerName = consumer.getConsumerName();
    final int maxRetries = annotation.maxRetries();

    if (maxRetries <= 0) {
      log.debug("消费者 {} 未启用重试功能", consumerName);
      return;
    }

    // 从消息属性中获取当前重试次数
    int currentRetries = 0;
    if (message.properties() != null && message.properties().getHeaders() != null) {
      final Object retryCount = message.properties().getHeaders().get("x-retry-count");
      if (retryCount instanceof Number) {
        currentRetries = ((Number) retryCount).intValue();
      }
    }

    if (currentRetries >= maxRetries) {
      log.warn("消费者 {} 重试次数已达上限 {}, 丢弃消息", consumerName, maxRetries);
      monitor.recordRetryExhausted(consumerName);
      return;
    }

    // 增加重试次数并延迟重新投递
    final int nextRetryCount = currentRetries + 1;
    final long retryDelay = annotation.retryDelayMs() * nextRetryCount; // 线性延迟

    log.info("消费者 {} 将在 {}ms 后进行第 {} 次重试，原因: {}",
        consumerName, retryDelay, nextRetryCount, errorReason);

    vertx.setTimer(retryDelay, timerId -> {
      try {
        // 重新处理消息
        monitor.recordRetry(consumerName);
        handleMessage(consumer, annotation, message);
      } catch (Exception retryError) {
        log.error("重试处理消息失败 - 消费者: {}", consumerName, retryError);
      }
    });
  }

  /**
   * 清理消费者资源
   */
  private void cleanupConsumer(final String consumerName) {
    try {
      activeConsumers.remove(consumerName);
      final RabbitMQClient client = consumerClients.remove(consumerName);
      if (client != null && client.isConnected()) {
        Future.await(client.stop());
      }
    } catch (Exception e) {
      log.error("清理消费者 {} 资源失败", consumerName, e);
    }
  }

  /**
   * 创建 JSON 消息属性
   */
  private JsonObject createJsonProperties() {
    return new JsonObject().put("content-type", "application/json");
  }

  // ========================================
  // 状态查询方法
  // ========================================

  public int getActiveConsumerCount() {
    return activeConsumers.size();
  }

  public int getRegisteredConsumerCount() {
    return registeredConsumers.size();
  }

  public boolean isConsumerActive(final String consumerName) {
    return activeConsumers.containsKey(consumerName);
  }

  public boolean isConsumerRegistered(final String consumerName) {
    return registeredConsumers.containsKey(consumerName);
  }

  public BasicConsumerMonitor getMonitor() {
    return monitor;
  }
}
