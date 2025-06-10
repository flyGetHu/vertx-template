package com.vertx.template.mq.enums;

/**
 * 消息投递模式枚举
 * 定义消息的持久化策略
 */
public enum MessageDeliveryMode {

  /** 非持久化 - 消息存储在内存中 */
  NON_PERSISTENT(1),

  /** 持久化 - 消息存储到磁盘 */
  PERSISTENT(2);

  private final int value;

  /**
   * 构造器
   *
   * @param value 投递模式值
   */
  MessageDeliveryMode(int value) {
    this.value = value;
  }

  /**
   * 获取投递模式值
   *
   * @return 投递模式数值
   */
  public int getValue() {
    return value;
  }

  /**
   * 根据数值获取枚举
   *
   * @param value 投递模式数值
   * @return 对应的枚举值
   */
  public static MessageDeliveryMode fromValue(int value) {
    for (MessageDeliveryMode mode : values()) {
      if (mode.value == value) {
        return mode;
      }
    }
    throw new IllegalArgumentException("不支持的投递模式: " + value);
  }
}
