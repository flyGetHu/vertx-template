package com.vertx.template.mq.example;

import com.vertx.template.mq.MQManager;
import io.vertx.core.json.JsonObject;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/** SimpleMQManager 使用示例 展示如何使用统一的MQ管理器进行消息的生产和消费 */
@Slf4j
@Singleton
public class SimpleMQExample {

  private final MQManager mqManager;

  @Inject
  public SimpleMQExample(final MQManager mqManager) {
    this.mqManager = mqManager;
  }

  /** 启动示例 在应用启动时调用此方法 */
  public void start() {
    log.info("=== SimpleMQManager 使用示例 ===");

    // 1. 启动消费者（自动扫描并启动所有带@RabbitConsumer注解的消费者）
    startConsumers();

    // 2. 发送测试消息
    sendTestMessages();

    // 3. 展示监控信息
    showMonitorInfo();
  }

  /** 启动消费者 */
  private void startConsumers() {
    log.info("1. 启动消费者系统...");

    // 自动扫描并启动所有消费者
    mqManager.scanAndStartConsumers("com.vertx.template");

    log.info("消费者启动完成，活跃消费者数量: {}", mqManager.getActiveConsumerCount());
  }

  /** 发送测试消息 */
  private void sendTestMessages() {
    log.info("2. 发送测试消息...");

    try {
      // 发送普通文本消息
      mqManager.sendToQueue("example.queue", "Hello SimpleMQManager!");

      // 发送JSON消息
      final JsonObject userEvent =
          new JsonObject()
              .put("userId", "12345")
              .put("action", "login")
              .put("timestamp", System.currentTimeMillis());

      mqManager.sendJsonToQueue("example.queue", userEvent);

      // 发送会触发重试的消息
      mqManager.sendToQueue("example.queue", "error - 这条消息会触发重试");

      // 发送到交换机
      mqManager.sendToExchange("user.exchange", "user.created", "新用户注册");

      log.info("测试消息发送完成");

    } catch (Exception e) {
      log.error("发送消息失败", e);
    }
  }

  /** 展示监控信息 */
  private void showMonitorInfo() {
    log.info("3. 当前系统状态...");

    // 等待一下让消息处理完成
    try {
      Thread.sleep(2000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    log.info("活跃消费者数量: {}", mqManager.getActiveConsumerCount());
    log.info("已注册消费者数量: {}", mqManager.getRegisteredConsumerCount());

    // 获取监控数据
    final JsonObject allStats = mqManager.getMonitor().getAllStats();
    log.info("监控统计: {}", allStats.encodePrettily());
  }

  /** 演示运行时管理功能 */
  public void demonstrateRuntimeManagement() {
    log.info("=== 运行时管理演示 ===");

    // 检查消费者状态
    final boolean isActive = mqManager.isConsumerActive("ExampleConsumer");
    log.info("ExampleConsumer 是否活跃: {}", isActive);

    if (isActive) {
      // 停止特定消费者
      mqManager.stopConsumer("ExampleConsumer");
      log.info("已停止 ExampleConsumer");

      // 重新启动
      mqManager.startConsumer("ExampleConsumer");
      log.info("已重新启动 ExampleConsumer");
    }
  }

  /** 清理资源 */
  public void cleanup() {
    log.info("=== 清理资源 ===");

    // 停止所有消费者
    mqManager.stopAllConsumers();
    log.info("所有消费者已停止");

    // 重置监控统计
    mqManager.getMonitor().resetAllStats();
    log.info("监控统计已重置");
  }
}
