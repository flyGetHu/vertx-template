package com.vertx.template.mq;

import com.google.inject.Injector;
import com.vertx.template.mq.config.RabbitMqConfig;
import com.vertx.template.mq.connection.ChannelPool;
import com.vertx.template.mq.consumer.BasicConsumerMonitor;
import com.vertx.template.mq.consumer.ConsumerRetryManager;
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

/** 简化的MQ管理器 统一管理消息的生产和消费功能 */
@Slf4j
@Singleton
public class MQManager {

  // 消费者相关字段
  private final Vertx vertx;
  private final RabbitMqConfig config;
  private final Injector injector;
  private final BasicConsumerMonitor monitor;
  private final ConsumerRetryManager retryManager;

  // 生产者相关字段
  private final ChannelPool channelPool;

  // 消费者存储
  private final Map<String, RabbitMQClient> consumerClients = new ConcurrentHashMap<>();
  private final Map<String, RabbitMQConsumer> activeConsumers = new ConcurrentHashMap<>();
  private final Map<String, MessageConsumer> registeredConsumers = new ConcurrentHashMap<>();
  private final Map<String, RabbitConsumer> consumerAnnotations = new ConcurrentHashMap<>();

  // 健康检查定时器
  private Long healthCheckTimerId;

  @Inject
  public MQManager(
      final Vertx vertx,
      final RabbitMqConfig config,
      final Injector injector,
      final BasicConsumerMonitor monitor,
      final ConsumerRetryManager retryManager,
      final ChannelPool channelPool) {
    this.vertx = vertx;
    this.config = config;
    this.injector = injector;
    this.monitor = monitor;
    this.retryManager = retryManager;
    this.channelPool = channelPool;
  }

  // ========================================
  // 消费者管理功能
  // ========================================

  /** 自动扫描并启动所有消费者 */
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

      // 启动消费者健康检查
      startConsumerHealthCheck();
    } catch (Exception e) {
      log.error("扫描消费者失败", e);
      throw new RuntimeException("扫描消费者失败", e);
    }
  }

  /** 注册并启动单个消费者 */
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

  /** 启动指定消费者 */
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

      // 设置QoS - Prefetch Count
      final int prefetchCount = annotation.prefetchCount();

      if (prefetchCount > 0) {
        Future.await(client.basicQos(prefetchCount));
        log.info("消费者 {} QoS设置完成: prefetchCount={}", consumerName, prefetchCount);
      } else {
        log.warn("消费者 {} 设置了无限制的prefetchCount，可能导致内存问题", consumerName);
      }

      // 创建消费者（队列必须预先存在）
      final QueueOptions options = new QueueOptions().setAutoAck(annotation.autoAck()).setMaxInternalQueueSize(1000);

      final RabbitMQConsumer rabbitConsumer = Future.await(client.basicConsumer(annotation.queueName(), options));

      // 设置消息处理器
      rabbitConsumer.handler(message -> handleMessage(consumer, annotation, message));

      activeConsumers.put(consumerName, rabbitConsumer);
      monitor.registerConsumer(consumerName);
      retryManager.registerConsumer(consumerName);

      log.info(
          "消费者 {} 启动成功 - 队列: {}, prefetch: {}",
          consumerName,
          annotation.queueName(),
          prefetchCount);

    } catch (Exception cause) {
      log.error("启动消费者 {} 失败 - 请确保队列 {} 已存在", consumerName, annotation.queueName(), cause);
      cleanupConsumer(consumerName);
      throw new RuntimeException("启动消费者失败: " + consumerName + "，请确保队列已存在", cause);
    }
  }

  /** 停止指定消费者 */
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
      retryManager.unregisterConsumer(consumerName);
      log.info("消费者 {} 已停止", consumerName);
    } catch (Exception cause) {
      log.error("停止消费者 {} 失败", consumerName, cause);
      throw new RuntimeException("停止消费者失败: " + consumerName, cause);
    }
  }

  /** 停止所有消费者 */
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

      // 停止健康检查
      stopHealthCheck();

      log.info("所有消费者已停止");

    } catch (Exception cause) {
      log.error("停止所有消费者失败", cause);
      throw new RuntimeException("停止所有消费者失败", cause);
    }
  }

  // ========================================
  // 消息生产功能
  // ========================================

  /** 发送简单消息到队列 */
  public void sendToQueue(final String queueName, final String message) {
    sendToQueue(queueName, message, null);
  }

  /** 发送消息到队列（带属性） */
  public void sendToQueue(
      final String queueName, final String message, final JsonObject properties) {
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

  /** 发送消息到交换机 */
  public void sendToExchange(
      final String exchangeName, final String routingKey, final String message) {
    sendToExchange(exchangeName, routingKey, message, null);
  }

  /** 发送消息到交换机（带属性） */
  public void sendToExchange(
      final String exchangeName,
      final String routingKey,
      final String message,
      final JsonObject properties) {
    final RabbitMQClient client = channelPool.borrowClient();
    try {
      // 直接发送消息，不创建交换机
      Future.await(client.basicPublish(exchangeName, routingKey, Buffer.buffer(message)));
      log.debug("消息发送成功 - 交换机: {}, 路由键: {}, 消息: {}", exchangeName, routingKey, message);
    } catch (Exception cause) {
      log.error(
          "发送消息到交换机失败 - 交换机: {}, 路由键: {}, 消息: {} - 请确保交换机已存在",
          exchangeName,
          routingKey,
          message,
          cause);
      throw new RuntimeException("发送消息到交换机失败，请确保交换机 [" + exchangeName + "] 已存在", cause);
    } finally {
      channelPool.returnClient(client);
    }
  }

  /** 发送 JSON 消息到队列 */
  public void sendJsonToQueue(final String queueName, final JsonObject jsonData) {
    sendToQueue(queueName, jsonData.encode(), createJsonProperties());
  }

  /** 发送 JSON 消息到交换机 */
  public void sendJsonToExchange(
      final String exchangeName, final String routingKey, final JsonObject jsonData) {
    sendToExchange(exchangeName, routingKey, jsonData.encode(), createJsonProperties());
  }

  // ========================================
  // 内部辅助方法
  // ========================================

  /** 处理接收到的消息 */
  private void handleMessage(
      final MessageConsumer consumer,
      final RabbitConsumer annotation,
      final RabbitMQMessage message) {
    final String consumerName = consumer.getConsumerName();
    final long startTime = System.currentTimeMillis();

    try {
      final Boolean result = consumer.handleMessage(message);
      final long processingTime = System.currentTimeMillis() - startTime;

      if (result != null && result) {
        monitor.recordSuccess(consumerName, processingTime);
        log.debug("消息处理成功 - 消费者: {}", consumerName);

        // 手动确认消息（仅在非自动确认模式下）
        if (!annotation.autoAck()) {
          try {
            // 获取对应的客户端并确认消息
            final RabbitMQClient client = consumerClients.get(consumerName);
            if (client != null && client.isConnected()) {
              Future.await(client.basicAck(message.envelope().getDeliveryTag(), false));
              log.debug(
                  "消息已确认 - 消费者: {}, deliveryTag: {}",
                  consumerName,
                  message.envelope().getDeliveryTag());
            }
          } catch (Exception ackException) {
            log.error(
                "确认消息失败 - 消费者: {}, deliveryTag: {}",
                consumerName,
                message.envelope().getDeliveryTag(),
                ackException);
          }
        }
      } else {
        monitor.recordFailure(consumerName, processingTime);
        log.warn("消息处理失败 - 消费者: {}", consumerName);

        // 在非自动确认模式下，根据重试策略决定是否NACK
        if (!annotation.autoAck()) {
          final int maxRetries = annotation.maxRetries();

          // 从消息属性中获取当前重试次数
          int currentRetries = 0;
          if (message.properties() != null && message.properties().getHeaders() != null) {
            final Object retryCount = message.properties().getHeaders().get("x-retry-count");
            if (retryCount instanceof Number) {
              currentRetries = ((Number) retryCount).intValue();
            }
          }

          if (currentRetries >= maxRetries) {
            // 重试次数已达上限，拒绝消息且不重新入队
            try {
              final RabbitMQClient client = consumerClients.get(consumerName);
              if (client != null && client.isConnected()) {
                Future.await(client.basicNack(message.envelope().getDeliveryTag(), false, false));
                log.warn(
                    "消息重试次数已达上限，已拒绝 - 消费者: {}, deliveryTag: {}",
                    consumerName,
                    message.envelope().getDeliveryTag());
              }
            } catch (Exception nackException) {
              log.error(
                  "拒绝消息失败 - 消费者: {}, deliveryTag: {}",
                  consumerName,
                  message.envelope().getDeliveryTag(),
                  nackException);
            }
          } else {
            // 还可以重试，暂不确认消息，让重试机制处理
            log.info(
                "消息处理失败但可重试 - 消费者: {}, 当前重试次数: {}/{}", consumerName, currentRetries, maxRetries);
          }
        }

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

      // 在非自动确认模式下，根据重试策略决定是否NACK
      if (!annotation.autoAck()) {
        final int maxRetries = annotation.maxRetries();

        // 从消息属性中获取当前重试次数
        int currentRetries = 0;
        if (message.properties() != null && message.properties().getHeaders() != null) {
          final Object retryCount = message.properties().getHeaders().get("x-retry-count");
          if (retryCount instanceof Number) {
            currentRetries = ((Number) retryCount).intValue();
          }
        }

        if (currentRetries >= maxRetries) {
          // 重试次数已达上限，拒绝消息且不重新入队
          try {
            final RabbitMQClient client = consumerClients.get(consumerName);
            if (client != null && client.isConnected()) {
              Future.await(client.basicNack(message.envelope().getDeliveryTag(), false, false));
              log.warn(
                  "消息异常处理重试次数已达上限，已拒绝 - 消费者: {}, deliveryTag: {}",
                  consumerName,
                  message.envelope().getDeliveryTag());
            }
          } catch (Exception nackException) {
            log.error(
                "拒绝异常消息失败 - 消费者: {}, deliveryTag: {}",
                consumerName,
                message.envelope().getDeliveryTag(),
                nackException);
          }
        } else {
          // 还可以重试，暂不确认消息，让重试机制处理
          log.info("消息异常处理但可重试 - 消费者: {}, 当前重试次数: {}/{}", consumerName, currentRetries, maxRetries);
        }
      }

      // 简单重试机制
      handleRetry(consumer, annotation, message, cause.getMessage());
    }
  }

  /** 简单的重试处理 */
  private void handleRetry(
      final MessageConsumer consumer,
      final RabbitConsumer annotation,
      final RabbitMQMessage message,
      final String errorReason) {
    final String consumerName = consumer.getConsumerName();
    final int maxRetries = annotation.maxRetries();

    if (maxRetries <= 0) {
      log.debug("消费者 {} 未启用重试功能", consumerName);
      // 在非自动确认模式下，如果不重试则直接拒绝消息
      if (!annotation.autoAck()) {
        try {
          final RabbitMQClient client = consumerClients.get(consumerName);
          if (client != null && client.isConnected()) {
            Future.await(client.basicNack(message.envelope().getDeliveryTag(), false, false));
            log.debug(
                "未启用重试，消息已拒绝 - 消费者: {}, deliveryTag: {}",
                consumerName,
                message.envelope().getDeliveryTag());
          }
        } catch (Exception nackException) {
          log.error(
              "拒绝消息失败 - 消费者: {}, deliveryTag: {}",
              consumerName,
              message.envelope().getDeliveryTag(),
              nackException);
        }
      }
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
      // 在非自动确认模式下，拒绝消息且不重新入队
      if (!annotation.autoAck()) {
        try {
          final RabbitMQClient client = consumerClients.get(consumerName);
          if (client != null && client.isConnected()) {
            Future.await(client.basicNack(message.envelope().getDeliveryTag(), false, false));
            log.debug(
                "重试次数已达上限，消息已拒绝 - 消费者: {}, deliveryTag: {}",
                consumerName,
                message.envelope().getDeliveryTag());
          }
        } catch (Exception nackException) {
          log.error(
              "拒绝消息失败 - 消费者: {}, deliveryTag: {}",
              consumerName,
              message.envelope().getDeliveryTag(),
              nackException);
        }
      }
      return;
    }

    // 在手动确认模式下，使用 nack 重新入队来实现重试
    if (!annotation.autoAck()) {
      final long retryDelay = annotation.retryDelayMs();

      log.info(
          "消费者 {} 将在 {}ms 后通过重新入队进行重试，当前重试次数: {}/{}, 原因: {}",
          consumerName,
          retryDelay,
          currentRetries + 1,
          maxRetries,
          errorReason);

      // 延迟后重新入队
      vertx.setTimer(
          retryDelay,
          timerId -> {
            try {
              final RabbitMQClient client = consumerClients.get(consumerName);
              if (client != null && client.isConnected()) {
                // 拒绝消息并重新入队，让消息重新被消费
                Future.await(client.basicNack(message.envelope().getDeliveryTag(), false, true));
                monitor.recordRetry(consumerName);
                log.debug(
                    "消息已重新入队进行重试 - 消费者: {}, deliveryTag: {}",
                    consumerName,
                    message.envelope().getDeliveryTag());
              }
            } catch (Exception nackException) {
              log.error(
                  "重新入队消息失败 - 消费者: {}, deliveryTag: {}",
                  consumerName,
                  message.envelope().getDeliveryTag(),
                  nackException);
            }
          });
    } else {
      // 自动确认模式下，使用原有的延迟重新处理逻辑
      final int nextRetryCount = currentRetries + 1;
      final long retryDelay = annotation.retryDelayMs() * nextRetryCount; // 线性延迟

      log.info(
          "消费者 {} 将在 {}ms 后进行第 {} 次重试，原因: {}",
          consumerName,
          retryDelay,
          nextRetryCount,
          errorReason);

      vertx.setTimer(
          retryDelay,
          timerId -> {
            try {
              // 重新处理消息
              monitor.recordRetry(consumerName);
              handleMessage(consumer, annotation, message);
            } catch (Exception retryError) {
              log.error("重试处理消息失败 - 消费者: {}", consumerName, retryError);
            }
          });
    }
  }

  /** 清理消费者资源 */
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

  /** 创建 JSON 消息属性 */
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

  public ConsumerRetryManager getRetryManager() {
    return retryManager;
  }

  // ========================================
  // 消费者健康检查和重连功能
  // ========================================

  /**
   * 启动消费者健康检查
   */
  private void startConsumerHealthCheck() {
    // 如果没有消费者，不启动健康检查
    if (registeredConsumers.isEmpty()) {
      log.debug("无消费者注册，跳过健康检查启动");
      return;
    }

    // 使用默认的健康检查间隔（30秒）
    final long healthCheckInterval = 30000L;

    healthCheckTimerId = vertx.setPeriodic(healthCheckInterval, id -> {
      try {
        performHealthCheck();
      } catch (Exception e) {
        log.error("消费者健康检查执行异常", e);
      }
    });

    log.info("消费者健康检查已启动，间隔: {}ms", healthCheckInterval);
  }

  /**
   * 执行健康检查
   */
  private void performHealthCheck() {
    if (registeredConsumers.isEmpty()) {
      return;
    }

    log.debug("执行消费者健康检查，共检查 {} 个消费者", registeredConsumers.size());

    for (final String consumerName : registeredConsumers.keySet()) {
      final RabbitConsumer annotation = consumerAnnotations.get(consumerName);

      // 检查是否启用自动重连
      if (annotation == null || !annotation.autoReconnect()) {
        continue;
      }

      // 检查消费者连接状态
      if (!isConsumerConnected(consumerName)) {
        log.warn("检测到消费者 {} 连接断开，触发重连", consumerName);
        monitor.recordDisconnection(consumerName);
        triggerConsumerReconnect(consumerName);
      }
    }
  }

  /**
   * 检查消费者是否连接正常
   */
  private boolean isConsumerConnected(final String consumerName) {
    final RabbitMQClient client = consumerClients.get(consumerName);
    final RabbitMQConsumer consumer = activeConsumers.get(consumerName);

    // 基础连接检查
    if (client == null || !client.isConnected() || consumer == null) {
      return false;
    }

    // 检查消费者连接是否正常
    try {
      // 使用轻量级连接检查，不创建任何新资源
      return isConsumerHealthy(consumerName, client);
    } catch (Exception e) {
      log.debug("消费者 {} 健康检查失败: {}", consumerName, e.getMessage());
      return false;
    }
  }

  /**
   * 执行消费者心跳检查
   *
   * <p>
   * 使用轻量级连接检查，避免创建临时队列
   */
  private boolean isConsumerHealthy(final String consumerName, final RabbitMQClient client) {
    try {
      // 只检查客户端连接状态，避免创建任何新资源
      // 这是最轻量级的健康检查方式
      final boolean isConnected = client.isConnected();

      if (isConnected) {
        log.debug("消费者 {} 健康检查通过 - 连接正常", consumerName);
      } else {
        log.debug("消费者 {} 健康检查失败 - 连接断开", consumerName);
      }

      return isConnected;

    } catch (Exception e) {
      log.debug("消费者 {} 健康检查异常: {}", consumerName, e.getMessage());
      return false;
    }
  }

  /**
   * 触发消费者重连
   */
  private void triggerConsumerReconnect(final String consumerName) {
    // 检查是否已在重试中
    if (retryManager.isRetrying(consumerName)) {
      log.debug("消费者 {} 已在重试中，跳过重复触发", consumerName);
      return;
    }

    // 检查是否已停止重试
    if (retryManager.isStopped(consumerName)) {
      log.debug("消费者 {} 重试已停止，跳过触发", consumerName);
      return;
    }

    // 调度重连任务
    retryManager.scheduleRetry(consumerName, () -> {
      try {
        reconnectConsumer(consumerName);
      } catch (Exception e) {
        log.error("消费者 {} 重连失败", consumerName, e);
        throw new RuntimeException("消费者重连失败", e);
      }
    });
  }

  /**
   * 重连消费者
   */
  private void reconnectConsumer(final String consumerName) {
    log.info("开始重连消费者: {}", consumerName);

    final MessageConsumer consumer = registeredConsumers.get(consumerName);
    final RabbitConsumer annotation = consumerAnnotations.get(consumerName);

    if (consumer == null || annotation == null) {
      log.error("消费者 {} 信息缺失，无法重连", consumerName);
      return;
    }

    try {
      // 清理旧连接
      cleanupConsumerForReconnect(consumerName);

      // 重新启动消费者
      startConsumerInternal(consumerName, consumer, annotation);

      // 记录重连成功
      retryManager.recordSuccess(consumerName);
      monitor.recordReconnection(consumerName);

      log.info("消费者 {} 重连成功", consumerName);

    } catch (Exception e) {
      log.error("消费者 {} 重连失败", consumerName, e);
      throw e;
    }
  }

  /**
   * 重新启动消费者的内部实现
   */
  private void startConsumerInternal(
      final String consumerName,
      final MessageConsumer consumer,
      final RabbitConsumer annotation) {

    log.debug("内部启动消费者: {} - 队列: {}", consumerName, annotation.queueName());

    // 为每个消费者创建独立的客户端
    final RabbitMQClient client = RabbitMQClient.create(vertx, config.toVertxOptions());
    consumerClients.put(consumerName, client);

    Future.await(client.start());

    // 设置QoS - Prefetch Count
    final int prefetchCount = annotation.prefetchCount();
    if (prefetchCount > 0) {
      Future.await(client.basicQos(prefetchCount));
      log.debug("消费者 {} QoS设置完成: prefetchCount={}", consumerName, prefetchCount);
    }

    // 创建消费者（队列必须预先存在）
    final QueueOptions options = new QueueOptions()
        .setAutoAck(annotation.autoAck())
        .setMaxInternalQueueSize(1000);

    final RabbitMQConsumer rabbitConsumer = Future.await(
        client.basicConsumer(annotation.queueName(), options));

    // 设置消息处理器
    rabbitConsumer.handler(message -> handleMessage(consumer, annotation, message));

    activeConsumers.put(consumerName, rabbitConsumer);

    log.debug("消费者 {} 内部启动完成", consumerName);
  }

  /**
   * 清理消费者连接以便重连
   */
  private void cleanupConsumerForReconnect(final String consumerName) {
    try {
      // 移除活跃消费者
      activeConsumers.remove(consumerName);

      // 关闭客户端连接
      final RabbitMQClient client = consumerClients.remove(consumerName);
      if (client != null) {
        try {
          if (client.isConnected()) {
            Future.await(client.stop());
          }
        } catch (Exception e) {
          log.warn("关闭消费者 {} 客户端连接时发生异常: {}", consumerName, e.getMessage());
        }
      }

      log.debug("消费者 {} 连接已清理", consumerName);
    } catch (Exception e) {
      log.error("清理消费者 {} 连接失败", consumerName, e);
    }
  }

  /**
   * 停止健康检查
   */
  private void stopHealthCheck() {
    if (healthCheckTimerId != null) {
      vertx.cancelTimer(healthCheckTimerId);
      healthCheckTimerId = null;
      log.info("消费者健康检查已停止");
    }
  }

  /**
   * 手动重置消费者重试状态
   */
  public void resetConsumerRetry(final String consumerName) {
    retryManager.resetConsumer(consumerName);
    log.info("消费者 {} 重试状态已手动重置", consumerName);
  }

  /**
   * 获取消费者重连状态摘要
   */
  public String getConsumerRetryStatusSummary() {
    return retryManager.getRetryStatusSummary();
  }
}
