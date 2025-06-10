package com.vertx.template.mq.config;

import com.vertx.template.mq.enums.ExchangeType;
import com.vertx.template.mq.enums.QueueProperties;
import io.vertx.core.json.JsonObject;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 消费者配置类
 * 包含队列名称、交换机、路由键等队列特定配置
 */
@Data
public class ConsumerConfig {

  /** 默认QoS值 */
  private static final int DEFAULT_QOS = 1;
  /** 默认消费者数量 */
  private static final int DEFAULT_CONSUMER_COUNT = 1;

  /** 队列名称 */
  private String queueName;

  /** 交换机名称 */
  private String exchangeName;

  /** 交换机类型 */
  private ExchangeType exchangeType = ExchangeType.DIRECT;

  /** 路由键 */
  private String routingKey;

  /** 是否持久化队列 */
  private boolean durable = true;

  /** 是否排他性队列 */
  private boolean exclusive = false;

  /** 是否自动删除队列 */
  private boolean autoDelete = false;

  /** 是否自动确认消息 */
  private boolean autoAck = false;

  /** QoS预取数量 */
  private int qos = DEFAULT_QOS;

  /** 消费者数量 */
  private int consumerCount = DEFAULT_CONSUMER_COUNT;

  /** 队列参数 */
  private Map<String, Object> queueArguments = new HashMap<>();

  /**
   * 构造器 - 仅指定队列名称
   *
   * @param queueName 队列名称
   */
  public ConsumerConfig(String queueName) {
    this.queueName = queueName;
  }

  /**
   * 构造器 - 指定队列名称和交换机信息
   *
   * @param queueName    队列名称
   * @param exchangeName 交换机名称
   * @param routingKey   路由键
   */
  public ConsumerConfig(String queueName, String exchangeName, String routingKey) {
    this.queueName = queueName;
    this.exchangeName = exchangeName;
    this.routingKey = routingKey;
  }

  /**
   * 构造器 - 完整配置
   *
   * @param queueName    队列名称
   * @param exchangeName 交换机名称
   * @param exchangeType 交换机类型
   * @param routingKey   路由键
   */
  public ConsumerConfig(String queueName, String exchangeName,
      ExchangeType exchangeType, String routingKey) {
    this.queueName = queueName;
    this.exchangeName = exchangeName;
    this.exchangeType = exchangeType;
    this.routingKey = routingKey;
  }

  /**
   * 验证配置是否有效
   *
   * @return 如果配置有效返回true，否则返回false
   */
  public boolean isValid() {
    return queueName != null && !queueName.trim().isEmpty()
        && qos > 0
        && consumerCount > 0;
  }

  /**
   * 设置死信队列配置
   *
   * @param deadLetterExchange   死信交换机
   * @param deadLetterRoutingKey 死信路由键
   * @return 当前配置对象(链式调用)
   */
  public ConsumerConfig withDeadLetter(String deadLetterExchange, String deadLetterRoutingKey) {
    queueArguments.put(QueueProperties.DEAD_LETTER_EXCHANGE.getValue(), deadLetterExchange);
    if (deadLetterRoutingKey != null) {
      queueArguments.put(QueueProperties.DEAD_LETTER_ROUTING_KEY.getValue(), deadLetterRoutingKey);
    }
    return this;
  }

  /**
   * 设置消息TTL
   *
   * @param messageTtl 消息TTL(毫秒)
   * @return 当前配置对象(链式调用)
   */
  public ConsumerConfig withMessageTtl(Long messageTtl) {
    if (messageTtl != null) {
      queueArguments.put(QueueProperties.MESSAGE_TTL.getValue(), messageTtl);
    }
    return this;
  }

  /**
   * 设置队列最大长度
   *
   * @param maxLength 队列最大长度
   * @return 当前配置对象(链式调用)
   */
  public ConsumerConfig withMaxLength(Integer maxLength) {
    if (maxLength != null) {
      queueArguments.put(QueueProperties.MAX_LENGTH.getValue(), maxLength);
    }
    return this;
  }

  /**
   * 设置队列最大优先级
   *
   * @param maxPriority 最大优先级
   * @return 当前配置对象(链式调用)
   */
  public ConsumerConfig withMaxPriority(Integer maxPriority) {
    if (maxPriority != null) {
      queueArguments.put(QueueProperties.MAX_PRIORITY.getValue(), maxPriority);
    }
    return this;
  }

  /**
   * 设置队列过期时间
   *
   * @param expires 队列过期时间(毫秒)
   * @return 当前配置对象(链式调用)
   */
  public ConsumerConfig withExpires(Long expires) {
    if (expires != null) {
      queueArguments.put(QueueProperties.EXPIRES.getValue(), expires);
    }
    return this;
  }

  /**
   * 添加自定义队列参数
   *
   * @param key   参数键
   * @param value 参数值
   * @return 当前配置对象(链式调用)
   */
  public ConsumerConfig withArgument(String key, Object value) {
    if (key != null && value != null) {
      queueArguments.put(key, value);
    }
    return this;
  }

  /**
   * 获取队列参数的JsonObject格式
   *
   * @return 队列参数的JsonObject
   */
  public JsonObject getQueueArgumentsAsJson() {
    return new JsonObject(queueArguments);
  }
}
