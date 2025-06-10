package com.vertx.template.mq.enums;

/** RabbitMQ交换机类型枚举 定义支持的交换机类型 */
public enum ExchangeType {

  /** 直连交换机 */
  DIRECT("direct"),

  /** 主题交换机 */
  TOPIC("topic"),

  /** 扇形交换机 */
  FANOUT("fanout"),

  /** 头部交换机 */
  HEADERS("headers");

  private final String value;

  /**
   * 构造器
   *
   * @param value 交换机类型值
   */
  ExchangeType(String value) {
    this.value = value;
  }

  /**
   * 获取交换机类型值
   *
   * @return 交换机类型字符串
   */
  public String getValue() {
    return value;
  }

  /**
   * 根据字符串值获取枚举
   *
   * @param value 交换机类型字符串
   * @return 对应的枚举值
   */
  public static ExchangeType fromValue(String value) {
    for (ExchangeType type : values()) {
      if (type.value.equals(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("不支持的交换机类型: " + value);
  }
}
