package com.vertx.template.mq.consumer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 简化的RabbitMQ消费者注解
 * 只保留基础的消费者配置功能
 */
@Target({ ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
public @interface RabbitConsumer {

  /**
   * 队列名称（必填）
   */
  String queueName();

  /**
   * 是否自动确认消息
   *
   * @return 默认false（手动确认）
   */
  boolean autoAck() default false;

  /**
   * 是否启用该消费者
   *
   * @return 默认true
   */
  boolean enabled() default true;

  /**
   * 最大重试次数
   *
   * @return 默认3次，0表示不重试
   */
  int maxRetries() default 3;

  /**
   * 重试延迟时间（毫秒）
   *
   * @return 默认1000ms
   */
  long retryDelayMs() default 1000L;

  /**
   * 消费者描述
   *
   * @return 用于日志和监控
   */
  String description() default "";
}
