package com.vertx.template.mq.consumer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** RabbitMQ消费者注解 - 基于注解的声明式消费者配置 */
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface RabbitConsumer {

  /** 队列名称（必填） */
  String queueName();

  /**
   * 是否自动确认消息
   *
   * @return 默认false（手动确认，保证消息可靠性）
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
   * Prefetch Count - 消费者预取消息数量
   *
   * <p>控制消费者同时处理的未确认消息数量，用于流量控制和负载均衡：
   *
   * <ul>
   *   <li>0: 无限制（不推荐，可能导致内存溢出）
   *   <li>1: 严格的轮询分发，适合处理时间差异大的场景
   *   <li>10-50: 适合大多数场景的平衡值
   *   <li>100+: 高吞吐量场景，需要足够的内存
   * </ul>
   *
   * <p><strong>注意</strong>：由于每个消费者使用独立通道，此设置仅对当前消费者生效
   *
   * @return 默认20，在性能和资源消耗之间取得平衡
   */
  int prefetchCount() default 20;

  /**
   * 消费者描述
   *
   * @return 用于日志和监控
   */
  String description() default "";

  /**
   * 是否启用自动重连
   *
   * @return 默认true，当MQ断连时自动重新连接
   */
  boolean autoReconnect() default true;

  /**
   * 连接健康检查间隔（毫秒）
   *
   * @return 默认30秒，设置为0禁用健康检查
   */
  long healthCheckInterval() default 30000L;
}
