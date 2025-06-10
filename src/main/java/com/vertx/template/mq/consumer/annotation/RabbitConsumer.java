package com.vertx.template.mq.consumer.annotation;

import com.vertx.template.mq.enums.ExchangeType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * RabbitMQ消费者注解
 * 用于标记消费者方法，并配置队列信息
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface RabbitConsumer {

  /**
   * 队列名称
   *
   * @return 队列名称
   */
  String queue();

  /**
   * 交换机名称
   *
   * @return 交换机名称，默认为空（使用默认交换机）
   */
  String exchange() default "";

  /**
   * 交换机类型
   *
   * @return 交换机类型，默认为直连交换机
   */
  ExchangeType exchangeType() default ExchangeType.DIRECT;

  /**
   * 路由键
   *
   * @return 路由键，默认为空
   */
  String routingKey() default "";

  /**
   * 是否持久化队列
   *
   * @return 是否持久化，默认为true
   */
  boolean durable() default true;

  /**
   * 是否排他性队列
   *
   * @return 是否排他性，默认为false
   */
  boolean exclusive() default false;

  /**
   * 是否自动删除队列
   *
   * @return 是否自动删除，默认为false
   */
  boolean autoDelete() default false;

  /**
   * 是否自动确认消息
   *
   * @return 是否自动确认，默认为false（手动确认）
   */
  boolean autoAck() default false;

  /**
   * QoS预取数量
   *
   * @return 预取数量，默认为1
   */
  int qos() default 1;

  /**
   * 消费者数量
   *
   * @return 消费者数量，默认为1
   */
  int consumerCount() default 1;

  /**
   * 死信交换机
   *
   * @return 死信交换机名称，默认为空
   */
  String deadLetterExchange() default "";

  /**
   * 死信路由键
   *
   * @return 死信路由键，默认为空
   */
  String deadLetterRoutingKey() default "";

  /**
   * 消息TTL（毫秒）
   *
   * @return 消息TTL，默认为-1（无限制）
   */
  long messageTtl() default -1;

  /**
   * 队列最大长度
   *
   * @return 队列最大长度，默认为-1（无限制）
   */
  int maxLength() default -1;

  /**
   * 队列最大优先级
   *
   * @return 最大优先级，默认为-1（无优先级）
   */
  int maxPriority() default -1;

  /**
   * 队列过期时间（毫秒）
   *
   * @return 队列过期时间，默认为-1（无限制）
   */
  long expires() default -1;

  /**
   * 是否启用该消费者
   *
   * @return 是否启用，默认为true
   */
  boolean enabled() default true;

  /**
   * 消费者组
   *
   * @return 消费者组名称，用于管理和监控
   */
  String group() default "default";

  /**
   * 消费者描述
   *
   * @return 消费者描述信息
   */
  String description() default "";
}
