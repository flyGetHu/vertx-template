package com.vertx.template.mq.consumer.retry;

import io.vertx.rabbitmq.RabbitMQMessage;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/** 可重试消息包装类 用于包装原始消息并携带重试相关的元数据 */
@Data
@Slf4j
public class RetryableMessage {

  /** 原始消息 */
  private final RabbitMQMessage originalMessage;

  /** 消费者名称 */
  private final String consumerName;

  /** 重试次数 */
  private int retryCount = 0;

  /** 首次处理时间 */
  private final LocalDateTime firstProcessTime;

  /** 最后重试时间 */
  private LocalDateTime lastRetryTime;

  /** 失败原因历史 */
  private final Map<Integer, String> failureHistory = new HashMap<>();

  /** 扩展属性 */
  private final Map<String, Object> properties = new HashMap<>();

  /**
   * 构造器
   *
   * @param originalMessage 原始消息
   * @param consumerName 消费者名称
   */
  public RetryableMessage(RabbitMQMessage originalMessage, String consumerName) {
    this.originalMessage = originalMessage;
    this.consumerName = consumerName;
    this.firstProcessTime = LocalDateTime.now();
    this.lastRetryTime = LocalDateTime.now();

    // 从原始消息中读取已有的重试次数
    this.retryCount = extractRetryCount(originalMessage);

    // 提取已有的失败历史
    extractFailureHistory(originalMessage);
  }

  /** 增加重试次数 */
  public void incrementRetryCount() {
    this.retryCount++;
    this.lastRetryTime = LocalDateTime.now();
    log.debug("消费者 {} 重试次数增加到 {}", consumerName, retryCount);
  }

  /**
   * 记录失败原因
   *
   * @param cause 失败原因
   */
  public void recordFailure(Throwable cause) {
    String errorMessage =
        cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    failureHistory.put(retryCount, errorMessage);
    log.debug("记录消费者 {} 第 {} 次处理失败: {}", consumerName, retryCount, errorMessage);
  }

  /**
   * 获取消息体
   *
   * @return 消息体字节数组
   */
  public byte[] getBody() {
    return originalMessage.body().getBytes();
  }

  /**
   * 获取消息体字符串
   *
   * @return 消息体字符串
   */
  public String getBodyAsString() {
    return originalMessage.body().toString();
  }

  /**
   * 检查是否达到最大重试次数
   *
   * @param maxRetries 最大重试次数
   * @return 是否达到最大重试次数
   */
  public boolean hasExceededMaxRetries(int maxRetries) {
    return retryCount >= maxRetries;
  }

  /**
   * 获取用于重新发送的消息属性
   *
   * @return 消息属性Map
   */
  public Map<String, Object> getRetryHeaders() {
    Map<String, Object> headers = new HashMap<>();

    // 原始消息的headers
    if (originalMessage.properties() != null && originalMessage.properties().getHeaders() != null) {
      headers.putAll(originalMessage.properties().getHeaders());
    }

    // 重试相关的headers
    headers.put("x-retry-count", retryCount);
    headers.put("x-first-process-time", firstProcessTime.toString());
    headers.put("x-last-retry-time", lastRetryTime.toString());
    headers.put("x-consumer-name", consumerName);

    // 失败历史
    for (Map.Entry<Integer, String> entry : failureHistory.entrySet()) {
      headers.put("x-failure-" + entry.getKey(), entry.getValue());
    }

    // 扩展属性
    headers.putAll(properties);

    return headers;
  }

  /**
   * 获取死信消息属性
   *
   * @param finalCause 最终失败原因
   * @return 死信消息属性Map
   */
  public Map<String, Object> getDeadLetterHeaders(Throwable finalCause) {
    Map<String, Object> headers = getRetryHeaders();

    // 死信相关属性
    headers.put("x-death-time", LocalDateTime.now().toString());
    headers.put(
        "x-death-reason",
        finalCause.getMessage() != null
            ? finalCause.getMessage()
            : finalCause.getClass().getSimpleName());
    headers.put("x-total-failures", failureHistory.size());
    headers.put("x-is-dead-letter", true);

    return headers;
  }

  /**
   * 设置扩展属性
   *
   * @param key 属性键
   * @param value 属性值
   */
  public void setProperty(String key, Object value) {
    properties.put(key, value);
  }

  /**
   * 获取扩展属性
   *
   * @param key 属性键
   * @return 属性值
   */
  public Object getProperty(String key) {
    return properties.get(key);
  }

  /**
   * 从原始消息中提取重试次数
   *
   * @param message 原始消息
   * @return 重试次数
   */
  private int extractRetryCount(RabbitMQMessage message) {
    if (message.properties() == null || message.properties().getHeaders() == null) {
      return 0;
    }

    Object retryCount = message.properties().getHeaders().get("x-retry-count");
    if (retryCount instanceof Integer) {
      return (Integer) retryCount;
    }

    return 0;
  }

  /**
   * 从原始消息中提取失败历史
   *
   * @param message 原始消息
   */
  private void extractFailureHistory(RabbitMQMessage message) {
    if (message.properties() == null || message.properties().getHeaders() == null) {
      return;
    }

    Map<String, Object> headers = message.properties().getHeaders();
    for (Map.Entry<String, Object> entry : headers.entrySet()) {
      String key = entry.getKey();
      if (key.startsWith("x-failure-") && entry.getValue() instanceof String) {
        try {
          int failureIndex = Integer.parseInt(key.substring("x-failure-".length()));
          failureHistory.put(failureIndex, (String) entry.getValue());
        } catch (NumberFormatException e) {
          log.debug("解析失败历史索引失败: {}", key);
        }
      }
    }
  }

  @Override
  public String toString() {
    return String.format(
        "RetryableMessage{consumerName='%s', retryCount=%d, failureCount=%d, firstProcessTime=%s}",
        consumerName, retryCount, failureHistory.size(), firstProcessTime);
  }
}
