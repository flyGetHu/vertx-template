package com.vertx.template.mq.example;

import com.vertx.template.mq.consumer.MessageConsumer;
import com.vertx.template.mq.consumer.RabbitConsumer;

import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.rabbitmq.RabbitMQMessage;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户创建事件消费者示例
 *
 * <p>
 * 展示如何实现一个完整的消息消费者：
 *
 * <ul>
 * <li>使用@RabbitConsumer注解配置消费者参数
 * <li>实现MessageConsumer接口处理消息
 * <li>支持JSON消息解析
 * <li>包含错误处理和重试机制
 * <li>生命周期回调处理
 * </ul>
 */
@RabbitConsumer(queueName = "user.created.queue", enabled = true, autoAck = false, // 手动确认，保证消息可靠性
    maxRetries = 3, retryDelayMs = 2000, // 2秒重试间隔
    prefetchCount = 10, // 每次预取10条消息
    description = "处理用户创建事件，发送欢迎邮件和初始化用户配置")
@Singleton
@Slf4j
public class UserCreatedConsumer implements MessageConsumer {

  @Override
  public String getConsumerName() {
    return "user-created-consumer";
  }

  @Override
  public Boolean handleMessage(RabbitMQMessage message) {
    try {
      // 解析消息内容
      final String messageBody = message.body().toString();
      log.info("收到用户创建消息: {}", messageBody);

      // 尝试解析为JSON
      final JsonObject userData = new JsonObject(messageBody);
      final String userId = userData.getString("userId");
      final String username = userData.getString("username");
      final String email = userData.getString("email");

      // 验证必需字段
      if (userId == null || username == null || email == null) {
        log.error("用户数据不完整: userId={}, username={}, email={}", userId, username, email);
        return false; // 处理失败，会重试
      }

      // 模拟业务处理
      processUserCreated(userId, username, email);

      log.info("用户创建事件处理完成: userId={}, username={}", userId, username);
      return true; // 处理成功

    } catch (Exception e) {
      if (e instanceof DecodeException) {
        log.error("序列化失败,message:{}", message.body().toString(), e);
        // 序列化失败，直接确认消息,不重试
        return true;
      }
      log.error("处理用户创建消息失败", e);
      return false; // 处理失败，会根据重试配置进行重试
    }
  }

  @Override
  public void onMessageFailed(RabbitMQMessage message, Throwable cause) {
    log.error("用户创建消息最终处理失败，消息将被丢弃: {}", message.body().toString(), cause);

    try {
      // 可以在这里添加失败消息的特殊处理逻辑
      // 例如：发送到死信队列、记录到数据库、发送告警等
      handleFailedMessage(message, cause);
    } catch (Exception e) {
      log.error("处理失败消息时发生异常", e);
    }
  }

  @Override
  public void onStart() {
    log.info("用户创建消费者启动，开始监听队列: user.created.queue");
    // 可以在这里进行一些初始化操作
    // 例如：预热缓存、检查依赖服务等
  }

  @Override
  public void onStop() {
    log.info("用户创建消费者停止");
    // 可以在这里进行一些清理操作
    // 例如：释放资源、保存状态等
  }

  /**
   * 处理用户创建的业务逻辑
   *
   * @param userId 用户ID
   * @param username 用户名
   * @param email 邮箱
   */
  private void processUserCreated(String userId, String username, String email) {
    // 1. 发送欢迎邮件
    sendWelcomeEmail(email, username);

    // 2. 初始化用户配置
    initializeUserSettings(userId);

    // 3. 记录用户创建日志
    logUserCreation(userId, username, email);

    // 模拟处理时间
    try {
      Thread.sleep(100); // 模拟处理耗时
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new RuntimeException("处理被中断", e);
    }
  }

  /** 发送欢迎邮件 */
  private void sendWelcomeEmail(String email, String username) {
    log.info("发送欢迎邮件到: {} (用户: {})", email, username);
    // 这里可以集成实际的邮件服务
  }

  /** 初始化用户配置 */
  private void initializeUserSettings(String userId) {
    log.info("初始化用户配置: userId={}", userId);
    // 这里可以进行用户配置的初始化
  }

  /** 记录用户创建日志 */
  private void logUserCreation(String userId, String username, String email) {
    log.info("用户创建记录: userId={}, username={}, email={}", userId, username, email);
    // 这里可以记录到审计日志或数据库
  }

  /** 处理失败的消息 */
  private void handleFailedMessage(RabbitMQMessage message, Throwable cause) {
    // 示例：将失败消息记录到数据库或发送到死信队列
    log.warn("记录失败消息: {}, 失败原因: {}", message.body().toString(), cause.getMessage());

    // 这里可以实现具体的失败处理逻辑，例如：
    // 1. 发送到死信队列
    // 2. 记录到失败日志表
    // 3. 发送告警通知
    // 4. 更新监控指标
  }
}
