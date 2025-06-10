package com.vertx.template.mq.consumer;

import io.vertx.rabbitmq.RabbitMQMessage;

/** 消息消费者接口 定义消息处理的基本方法 */
public interface MessageConsumer {

  /**
   * 处理接收到的消息
   *
   * @param message RabbitMQ消息
   * @return 处理结果，成功时返回true，失败时返回false或抛出异常
   */
  Boolean handleMessage(RabbitMQMessage message);

  /**
   * 获取消费者名称
   *
   * @return 消费者名称
   */
  default String getConsumerName() {
    return this.getClass().getSimpleName();
  }

  /**
   * 消息处理失败时的回调
   *
   * @param message 失败的消息
   * @param cause 失败原因
   */
  default void onMessageFailed(RabbitMQMessage message, Throwable cause) {
    // 默认实现：记录错误日志
    System.err.println("消息处理失败 - 消费者: " + getConsumerName() + ", 错误: " + cause.getMessage());
  }

  /** 消费者启动时的初始化回调 */
  default void onStart() {
    // 默认实现：无操作
  }

  /** 消费者停止时的清理回调 */
  default void onStop() {
    // 默认实现：无操作
  }
}
