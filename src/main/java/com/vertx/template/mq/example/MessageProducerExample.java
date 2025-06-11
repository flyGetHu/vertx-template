package com.vertx.template.mq.example;

import com.vertx.template.mq.MQManager;
import io.vertx.core.json.JsonObject;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息生产者使用示例
 *
 * <p>
 * 展示如何使用MQManager发送各种类型的消息：
 * <ul>
 * <li>发送简单文本消息到队列</li>
 * <li>发送JSON消息到队列</li>
 * <li>发送消息到交换机</li>
 * <li>发送带属性的消息</li>
 * <li>批量发送消息</li>
 * </ul>
 */
@Singleton
@Slf4j
public class MessageProducerExample {

  private final MQManager mqManager;

  @Inject
  public MessageProducerExample(MQManager mqManager) {
    this.mqManager = mqManager;
  }

  /**
   * 发送用户创建事件
   */
  public void sendUserCreatedEvent(String userId, String username, String email) {
    try {
      final JsonObject userData = new JsonObject()
          .put("userId", userId)
          .put("username", username)
          .put("email", email)
          .put("timestamp", System.currentTimeMillis())
          .put("eventType", "USER_CREATED");

      // 发送JSON消息到队列
      mqManager.sendJsonToQueue("user.created.queue", userData);

      log.info("用户创建事件已发送: userId={}, username={}", userId, username);

    } catch (Exception e) {
      log.error("发送用户创建事件失败: userId={}", userId, e);
      throw new RuntimeException("发送用户创建事件失败", e);
    }
  }

  /**
   * 发送订单状态更新事件
   */
  public void sendOrderStatusUpdate(String orderId, String status) {
    try {
      final JsonObject orderData = new JsonObject()
          .put("orderId", orderId)
          .put("status", status)
          .put("timestamp", System.currentTimeMillis());

      // 发送到队列
      mqManager.sendJsonToQueue("order.status.queue", orderData);

      log.info("订单状态更新事件已发送: orderId={}, status={}", orderId, status);

    } catch (Exception e) {
      log.error("发送订单状态更新失败: orderId={}", orderId, e);
      throw new RuntimeException("发送订单状态更新失败", e);
    }
  }

  /**
   * 发送通知消息到交换机
   */
  public void sendNotification(String userId, String message, String type) {
    try {
      final JsonObject notification = new JsonObject()
          .put("userId", userId)
          .put("message", message)
          .put("type", type)
          .put("timestamp", System.currentTimeMillis());

      // 根据通知类型选择路由键
      final String routingKey = getNotificationRoutingKey(type);

      // 发送到通知交换机
      mqManager.sendJsonToExchange("notification.exchange", routingKey, notification);

      log.info("通知消息已发送: userId={}, type={}, routingKey={}", userId, type, routingKey);

    } catch (Exception e) {
      log.error("发送通知消息失败: userId={}, type={}", userId, type, e);
      throw new RuntimeException("发送通知消息失败", e);
    }
  }

  /**
   * 发送简单文本消息
   */
  public void sendSimpleMessage(String queueName, String message) {
    try {
      mqManager.sendToQueue(queueName, message);
      log.info("简单消息已发送到队列: {}", queueName);

    } catch (Exception e) {
      log.error("发送简单消息失败: queue={}", queueName, e);
      throw new RuntimeException("发送简单消息失败", e);
    }
  }

  /**
   * 批量发送消息示例
   */
  public void sendBatchMessages(String queueName, java.util.List<JsonObject> messages) {
    log.info("开始批量发送消息: queue={}, count={}", queueName, messages.size());

    int successCount = 0;
    int failureCount = 0;

    for (JsonObject message : messages) {
      try {
        mqManager.sendJsonToQueue(queueName, message);
        successCount++;
      } catch (Exception e) {
        failureCount++;
        log.error("批量发送消息失败: {}", message.encode(), e);
      }
    }

    log.info("批量发送完成: 成功={}, 失败={}", successCount, failureCount);
  }

  /**
   * 发送延迟消息示例（使用消息属性）
   */
  public void sendDelayedMessage(String queueName, JsonObject message, long delayMs) {
    try {
      // 创建消息属性
      final JsonObject properties = new JsonObject()
          .put("content-type", "application/json")
          .put("x-delay", delayMs); // 延迟属性（需要交换机支持）

      mqManager.sendToQueue(queueName, message.encode(), properties);

      log.info("延迟消息已发送: queue={}, delay={}ms", queueName, delayMs);

    } catch (Exception e) {
      log.error("发送延迟消息失败: queue={}", queueName, e);
      throw new RuntimeException("发送延迟消息失败", e);
    }
  }

  /**
   * 发送优先级消息示例
   */
  public void sendPriorityMessage(String queueName, JsonObject message, int priority) {
    try {
      final JsonObject properties = new JsonObject()
          .put("content-type", "application/json")
          .put("priority", priority); // 优先级属性

      mqManager.sendToQueue(queueName, message.encode(), properties);

      log.info("优先级消息已发送: queue={}, priority={}", queueName, priority);

    } catch (Exception e) {
      log.error("发送优先级消息失败: queue={}", queueName, e);
      throw new RuntimeException("发送优先级消息失败", e);
    }
  }

  /**
   * 获取通知路由键
   */
  private String getNotificationRoutingKey(String notificationType) {
    switch (notificationType.toUpperCase()) {
      case "EMAIL":
        return "notification.email";
      case "SMS":
        return "notification.sms";
      case "PUSH":
        return "notification.push";
      case "SYSTEM":
        return "notification.system";
      default:
        return "notification.default";
    }
  }

  /**
   * 健康检查 - 发送测试消息
   */
  public boolean healthCheck() {
    try {
      final JsonObject testMessage = new JsonObject()
          .put("type", "health_check")
          .put("timestamp", System.currentTimeMillis())
          .put("source", "MessageProducerExample");

      mqManager.sendJsonToQueue("health.check.queue", testMessage);

      log.debug("健康检查消息发送成功");
      return true;

    } catch (Exception e) {
      log.error("健康检查失败", e);
      return false;
    }
  }
}
