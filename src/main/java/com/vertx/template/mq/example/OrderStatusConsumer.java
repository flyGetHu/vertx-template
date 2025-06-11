package com.vertx.template.mq.example;

import com.vertx.template.mq.consumer.MessageConsumer;
import com.vertx.template.mq.consumer.RabbitConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.rabbitmq.RabbitMQMessage;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * 订单状态更新消费者示例
 *
 * <p>
 * 展示不同的消费者配置选项：
 * <ul>
 * <li>高性能场景的配置（高prefetchCount）</li>
 * <li>自动确认模式</li>
 * <li>快速重试策略</li>
 * <li>批量处理逻辑</li>
 * </ul>
 */
@RabbitConsumer(queueName = "order.status.queue", enabled = true, autoAck = true, // 自动确认，提高性能
    maxRetries = 5, // 更多重试次数
    retryDelayMs = 500, // 快速重试
    prefetchCount = 50, // 高性能场景，预取更多消息
    description = "处理订单状态更新，高性能批量处理")
@Singleton
@Slf4j
public class OrderStatusConsumer implements MessageConsumer {

  @Override
  public String getConsumerName() {
    return "order-status-consumer";
  }

  @Override
  public Boolean handleMessage(RabbitMQMessage message) {
    try {
      final String messageBody = message.body().toString();
      final JsonObject orderData = new JsonObject(messageBody);

      final String orderId = orderData.getString("orderId");
      final String status = orderData.getString("status");
      final String timestamp = orderData.getString("timestamp");

      log.debug("处理订单状态更新: orderId={}, status={}, timestamp={}",
          orderId, status, timestamp);

      // 快速处理订单状态更新
      updateOrderStatus(orderId, status, timestamp);

      return true;

    } catch (Exception e) {
      log.error("处理订单状态更新失败", e);
      return false;
    }
  }

  @Override
  public void onStart() {
    log.info("订单状态消费者启动 - 高性能模式，prefetchCount=50");
  }

  @Override
  public void onStop() {
    log.info("订单状态消费者停止");
  }

  /**
   * 更新订单状态 - 高性能处理
   */
  private void updateOrderStatus(String orderId, String status, String timestamp) {
    // 模拟快速数据库更新
    log.debug("更新订单状态: {} -> {}", orderId, status);

    // 实际场景中可以：
    // 1. 批量更新数据库
    // 2. 更新缓存
    // 3. 发送状态变更通知
  }
}
