package com.vertx.template.examples.consumer;

import com.vertx.template.mq.consumer.MessageConsumer;
import com.vertx.template.mq.consumer.annotation.RabbitConsumer;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.rabbitmq.RabbitMQMessage;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/** 通知消费者示例 演示简单的消费者配置和消息处理 */
@Slf4j
@Singleton
@RabbitConsumer(
    queue = "notification.send",
    routingKey = "notification.email",
    description = "发送邮件通知")
public class NotificationConsumer implements MessageConsumer {

  /**
   * 处理通知消息
   *
   * @param message RabbitMQ消息
   * @return 处理结果的Future
   */
  @Override
  public Future<Boolean> handleMessage(RabbitMQMessage message) {
    try {
      // 解析消息内容
      String messageBody = message.body().toString();
      JsonObject notificationData = new JsonObject(messageBody);

      log.info("收到通知消息: {}", notificationData.encode());

      // 处理通知发送
      return sendNotification(notificationData)
          .onSuccess(result -> log.info("通知发送成功: {}", notificationData.getString("id")))
          .onFailure(
              cause ->
                  log.error(
                      "通知发送失败: {}, 错误: {}", notificationData.getString("id"), cause.getMessage()));

    } catch (Exception e) {
      log.error("解析通知消息失败", e);
      return Future.succeededFuture(false);
    }
  }

  /**
   * 发送通知
   *
   * @param notificationData 通知数据
   * @return 发送结果的Future
   */
  private Future<Boolean> sendNotification(JsonObject notificationData) {
    return Future.future(
        promise -> {
          Thread.startVirtualThread(
              () -> {
                try {
                  String type = notificationData.getString("type");
                  String recipient = notificationData.getString("recipient");
                  String subject = notificationData.getString("subject");
                  String content = notificationData.getString("content");

                  // 验证通知数据
                  if (type == null || recipient == null || content == null) {
                    promise.fail(new IllegalArgumentException("通知数据不完整"));
                    return;
                  }

                  // 模拟发送时间
                  Thread.sleep(50 + (long) (Math.random() * 100));

                  // 模拟5%的失败率
                  if (Math.random() < 0.05) {
                    promise.fail(new RuntimeException("通知服务暂时不可用"));
                    return;
                  }

                  // 发送成功
                  log.debug("通知发送完成 - 类型: {}, 收件人: {}, 主题: {}", type, recipient, subject);
                  promise.complete(true);

                } catch (Exception e) {
                  promise.fail(e);
                }
              });
        });
  }

  /**
   * 获取消费者名称
   *
   * @return 消费者名称
   */
  @Override
  public String getConsumerName() {
    return "NotificationConsumer";
  }
}
