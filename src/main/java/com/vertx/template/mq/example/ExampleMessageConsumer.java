package com.vertx.template.mq.example;

import com.vertx.template.mq.consumer.MessageConsumer;
import com.vertx.template.mq.consumer.RabbitConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.rabbitmq.RabbitMQMessage;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * 示例消息消费者 展示如何使用@RabbitConsumer注解和MessageConsumer接口
 *
 * <p>注意：此消费者需要预先创建以下RabbitMQ基础设施： 1. 队列：example.queue 2. 交换机：user.exchange 3.
 * 绑定关系：queue绑定到exchange，routing key为user.*
 */
@Slf4j
@Singleton
@RabbitConsumer(
    queueName = "example.queue",
    enabled = true,
    autoAck = false,
    maxRetries = 3,
    retryDelayMs = 1000)
public class ExampleMessageConsumer implements MessageConsumer {

  @Override
  public String getConsumerName() {
    return "ExampleConsumer";
  }

  @Override
  public void onStart() {
    log.info("示例消费者启动完成 - 正在监听队列: example.queue");
    log.info("提示：请确保RabbitMQ中已创建 example.queue 队列");
  }

  @Override
  public void onStop() {
    log.info("示例消费者已停止");
  }

  @Override
  public Boolean handleMessage(final RabbitMQMessage message) {
    final String body = message.body().toString();
    log.info("收到消息: {}", body);

    try {
      // 模拟消息处理
      processMessage(body);

      log.info("消息处理成功: {}", body);
      return true; // 返回true表示处理成功

    } catch (Exception e) {
      log.error("处理消息失败: {}", body, e);
      return false; // 返回false表示处理失败，框架会处理重试
    }
  }

  /** 处理消息的业务逻辑 */
  private void processMessage(final String messageBody) {
    // 模拟不同类型的消息处理
    if (messageBody.contains("error")) {
      throw new RuntimeException("模拟处理失败");
    }

    // 尝试解析JSON消息
    try {
      final JsonObject json = new JsonObject(messageBody);
      final String action = json.getString("action");
      final String userId = json.getString("userId");

      if (action != null && userId != null) {
        log.info("处理用户事件 - 用户ID: {}, 操作: {}", userId, action);

        // 根据不同的action执行不同的业务逻辑
        switch (action) {
          case "login":
            handleUserLogin(userId, json);
            break;
          case "logout":
            handleUserLogout(userId, json);
            break;
          case "register":
            handleUserRegister(userId, json);
            break;
          default:
            log.info("未知操作类型: {}", action);
        }
      } else {
        log.info("处理文本消息: {}", messageBody);
      }

    } catch (Exception e) {
      // 不是JSON格式，按文本消息处理
      log.info("处理文本消息: {}", messageBody);
    }

    // 模拟处理时间
    try {
      Thread.sleep(100);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /** 处理用户登录事件 */
  private void handleUserLogin(final String userId, final JsonObject event) {
    final long timestamp = event.getLong("timestamp", System.currentTimeMillis());
    log.info("用户 {} 于 {} 登录系统", userId, new java.util.Date(timestamp));

    // 这里可以添加具体的业务逻辑，比如：
    // 1. 记录登录日志
    // 2. 更新用户在线状态
    // 3. 发送登录通知
    // 4. 统计登录次数等
  }

  /** 处理用户登出事件 */
  private void handleUserLogout(final String userId, final JsonObject event) {
    final long timestamp = event.getLong("timestamp", System.currentTimeMillis());
    log.info("用户 {} 于 {} 登出系统", userId, new java.util.Date(timestamp));

    // 这里可以添加具体的业务逻辑，比如：
    // 1. 清理用户会话
    // 2. 更新用户离线状态
    // 3. 计算在线时长等
  }

  /** 处理用户注册事件 */
  private void handleUserRegister(final String userId, final JsonObject event) {
    final String username = event.getString("username", "未知用户");
    log.info("新用户注册 - 用户ID: {}, 用户名: {}", userId, username);

    // 这里可以添加具体的业务逻辑，比如：
    // 1. 发送欢迎邮件
    // 2. 初始化用户权限
    // 3. 创建用户目录
    // 4. 统计注册数量等
  }
}
