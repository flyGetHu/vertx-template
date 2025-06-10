package com.vertx.template.examples.consumer;

import com.vertx.template.mq.consumer.MessageConsumer;
import com.vertx.template.mq.consumer.annotation.RabbitConsumer;
import com.vertx.template.mq.enums.ExchangeType;
import io.vertx.core.json.JsonObject;
import io.vertx.rabbitmq.RabbitMQMessage;
import lombok.extern.slf4j.Slf4j;

/** 订单消息消费者示例 展示如何使用注解配置消费者 */
@Slf4j
@RabbitConsumer(
    queue = "order.queue",
    exchange = "order.exchange",
    exchangeType = ExchangeType.DIRECT,
    routingKey = "order.created",
    durable = true,
    autoAck = false,
    qos = 10,
    enabled = true)
public class OrderConsumer implements MessageConsumer {

  @Override
  public Boolean handleMessage(RabbitMQMessage message) {
    try {
      final String messageBody = message.body().toString();
      final JsonObject orderData = new JsonObject(messageBody);

      // 模拟处理订单消息
      final String orderId = orderData.getString("orderId");
      final String customerId = orderData.getString("customerId");
      final Double amount = orderData.getDouble("amount");

      log.info("开始处理订单消息 - 订单ID: {}, 客户ID: {}, 金额: {}", orderId, customerId, amount);

      // 模拟业务处理时间
      Thread.sleep(100);

      // 模拟处理逻辑
      processOrder(orderId, customerId, amount);

      log.info("订单消息处理完成 - 订单ID: {}", orderId);
      return true;

    } catch (Exception cause) {
      log.error("处理订单消息失败", cause);
      return false;
    }
  }

  @Override
  public void onStart() {
    log.info("订单消费者启动成功");
  }

  @Override
  public void onStop() {
    log.info("订单消费者已停止");
  }

  @Override
  public void onMessageFailed(RabbitMQMessage message, Throwable cause) {
    log.error("订单消息处理失败，消息将被拒绝", cause);

    try {
      final String messageBody = message.body().toString();
      final JsonObject failedOrder = new JsonObject(messageBody);
      final String orderId = failedOrder.getString("orderId");

      // 记录失败的订单信息
      recordFailedOrder(orderId, cause.getMessage());

    } catch (Exception e) {
      log.error("记录失败订单信息时发生错误", e);
    }
  }

  /**
   * 处理订单业务逻辑
   *
   * @param orderId 订单ID
   * @param customerId 客户ID
   * @param amount 金额
   */
  private void processOrder(String orderId, String customerId, Double amount) {
    // 模拟订单处理逻辑
    log.debug("执行订单处理逻辑 - 订单ID: {}, 客户ID: {}, 金额: {}", orderId, customerId, amount);

    // 这里可以添加实际的业务逻辑：
    // 1. 验证订单数据
    // 2. 检查库存
    // 3. 创建订单记录
    // 4. 发送确认邮件
    // 5. 更新用户积分等
  }

  /**
   * 记录失败的订单信息
   *
   * @param orderId 订单ID
   * @param errorMessage 错误信息
   */
  private void recordFailedOrder(String orderId, String errorMessage) {
    log.warn("记录失败订单 - 订单ID: {}, 错误信息: {}", orderId, errorMessage);

    // 这里可以添加失败订单的处理逻辑：
    // 1. 保存到失败队列
    // 2. 记录到数据库
    // 3. 发送告警通知
    // 4. 计划重试等
  }
}
