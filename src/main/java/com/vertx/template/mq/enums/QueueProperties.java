package com.vertx.template.mq.enums;

/** 队列属性枚举 定义队列的各种属性和参数 */
public enum QueueProperties {

  /** 队列消息TTL */
  MESSAGE_TTL("x-message-ttl"),

  /** 队列TTL */
  EXPIRES("x-expires"),

  /** 队列最大长度 */
  MAX_LENGTH("x-max-length"),

  /** 队列最大长度字节数 */
  MAX_LENGTH_BYTES("x-max-length-bytes"),

  /** 死信交换机 */
  DEAD_LETTER_EXCHANGE("x-dead-letter-exchange"),

  /** 死信路由键 */
  DEAD_LETTER_ROUTING_KEY("x-dead-letter-routing-key"),

  /** 队列模式 */
  QUEUE_MODE("x-queue-mode"),

  /** 最大优先级 */
  MAX_PRIORITY("x-max-priority");

  private final String value;

  /**
   * 构造器
   *
   * @param value 属性名称
   */
  QueueProperties(String value) {
    this.value = value;
  }

  /**
   * 获取属性名称
   *
   * @return 属性名称字符串
   */
  public String getValue() {
    return value;
  }

  /**
   * 根据字符串值获取枚举
   *
   * @param value 属性名称字符串
   * @return 对应的枚举值
   */
  public static QueueProperties fromValue(String value) {
    for (QueueProperties property : values()) {
      if (property.value.equals(value)) {
        return property;
      }
    }
    throw new IllegalArgumentException("不支持的队列属性: " + value);
  }
}
