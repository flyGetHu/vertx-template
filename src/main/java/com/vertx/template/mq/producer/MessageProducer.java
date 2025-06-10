package com.vertx.template.mq.producer;

import com.vertx.template.mq.connection.ChannelPool;
import com.vertx.template.mq.enums.ExchangeType;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.rabbitmq.RabbitMQClient;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

/**
 * 消息生产者
 * 提供统一的消息发送接口，支持事务、确认机制等高级特性
 */
@Slf4j
@Singleton
public class MessageProducer {

  private final ChannelPool channelPool;

  /**
   * 构造器
   *
   * @param channelPool 连接池
   */
  @Inject
  public MessageProducer(ChannelPool channelPool) {
    this.channelPool = channelPool;
  }

  /**
   * 发送简单消息到队列
   *
   * @param queueName 队列名称
   * @param message   消息内容
   */
  public void sendToQueue(String queueName, String message) {
    sendToQueue(queueName, message, null);
  }

  /**
   * 发送消息到队列（带属性）
   *
   * @param queueName  队列名称
   * @param message    消息内容
   * @param properties 消息属性
   */
  public void sendToQueue(String queueName, String message, JsonObject properties) {
    final RabbitMQClient client = Future.await(channelPool.borrowClient());
    try {
      Future.await(client.basicPublish("", queueName, Buffer.buffer(message)));
      log.debug("消息发送成功 - 队列: {}, 消息: {}", queueName, message);
    } catch (Exception cause) {
      log.error("发送消息到队列失败 - 队列: {}, 消息: {}", queueName, message, cause);
      throw new RuntimeException("发送消息到队列失败", cause);
    } finally {
      Future.await(channelPool.returnClient(client));
    }
  }

  /**
   * 发送消息到交换机
   *
   * @param exchangeName 交换机名称
   * @param routingKey   路由键
   * @param message      消息内容
   */
  public void sendToExchange(String exchangeName, String routingKey, String message) {
    sendToExchange(exchangeName, routingKey, message, null);
  }

  /**
   * 发送消息到交换机（带属性）
   *
   * @param exchangeName 交换机名称
   * @param routingKey   路由键
   * @param message      消息内容
   * @param properties   消息属性
   */
  public void sendToExchange(String exchangeName, String routingKey, String message, JsonObject properties) {
    final RabbitMQClient client = Future.await(channelPool.borrowClient());
    try {
      Future.await(client.basicPublish(exchangeName, routingKey, Buffer.buffer(message)));
      log.debug("消息发送成功 - 交换机: {}, 路由键: {}, 消息: {}", exchangeName, routingKey, message);
    } catch (Exception cause) {
      log.error("发送消息到交换机失败 - 交换机: {}, 路由键: {}, 消息: {}", exchangeName, routingKey, message, cause);
      throw new RuntimeException("发送消息到交换机失败", cause);
    } finally {
      Future.await(channelPool.returnClient(client));
    }
  }

  /**
   * 批量发送消息到队列
   *
   * @param queueName 队列名称
   * @param messages  消息列表
   */
  public void batchSendToQueue(String queueName, List<String> messages) {
    if (messages == null || messages.isEmpty()) {
      log.warn("批量发送消息列表为空");
      return;
    }

    final RabbitMQClient client = Future.await(channelPool.borrowClient());
    try {
      log.info("开始批量发送消息到队列: {}, 消息数量: {}", queueName, messages.size());

      for (final String message : messages) {
        Future.await(client.basicPublish("", queueName, Buffer.buffer(message)));
      }

      log.info("批量发送消息完成 - 队列: {}, 成功发送: {} 条", queueName, messages.size());
    } catch (Exception cause) {
      log.error("批量发送消息到队列失败 - 队列: {}", queueName, cause);
      throw new RuntimeException("批量发送消息到队列失败", cause);
    } finally {
      Future.await(channelPool.returnClient(client));
    }
  }

  /**
   * 批量发送消息到交换机
   *
   * @param exchangeName 交换机名称
   * @param routingKey   路由键
   * @param messages     消息列表
   */
  public void batchSendToExchange(String exchangeName, String routingKey, List<String> messages) {
    if (messages == null || messages.isEmpty()) {
      log.warn("批量发送消息列表为空");
      return;
    }

    final RabbitMQClient client = Future.await(channelPool.borrowClient());
    try {
      log.info("开始批量发送消息到交换机: {}, 路由键: {}, 消息数量: {}", exchangeName, routingKey, messages.size());

      for (final String message : messages) {
        Future.await(client.basicPublish(exchangeName, routingKey, Buffer.buffer(message)));
      }

      log.info("批量发送消息完成 - 交换机: {}, 成功发送: {} 条", exchangeName, messages.size());
    } catch (Exception cause) {
      log.error("批量发送消息到交换机失败 - 交换机: {}, 路由键: {}", exchangeName, routingKey, cause);
      throw new RuntimeException("批量发送消息到交换机失败", cause);
    } finally {
      Future.await(channelPool.returnClient(client));
    }
  }

  /**
   * 发送 JSON 消息到队列
   *
   * @param queueName 队列名称
   * @param jsonData  JSON数据
   */
  public void sendJsonToQueue(String queueName, JsonObject jsonData) {
    sendToQueue(queueName, jsonData.encode(), createJsonProperties());
  }

  /**
   * 发送 JSON 消息到交换机
   *
   * @param exchangeName 交换机名称
   * @param routingKey   路由键
   * @param jsonData     JSON数据
   */
  public void sendJsonToExchange(String exchangeName, String routingKey, JsonObject jsonData) {
    sendToExchange(exchangeName, routingKey, jsonData.encode(), createJsonProperties());
  }

  /**
   * 声明交换机
   *
   * @param exchangeName 交换机名称
   * @param exchangeType 交换机类型
   * @param durable      是否持久化
   * @param autoDelete   是否自动删除
   */
  public void declareExchange(String exchangeName, ExchangeType exchangeType, boolean durable, boolean autoDelete) {
    final RabbitMQClient client = Future.await(channelPool.borrowClient());
    try {
      Future.await(client.exchangeDeclare(exchangeName, exchangeType.getValue(), durable, autoDelete));
      log.info("交换机声明成功 - 名称: {}, 类型: {}, 持久化: {}, 自动删除: {}",
          exchangeName, exchangeType.getValue(), durable, autoDelete);
    } catch (Exception cause) {
      log.error("声明交换机失败 - 名称: {}, 类型: {}", exchangeName, exchangeType.getValue(), cause);
      throw new RuntimeException("声明交换机失败", cause);
    } finally {
      Future.await(channelPool.returnClient(client));
    }
  }

  /**
   * 声明队列
   *
   * @param queueName  队列名称
   * @param durable    是否持久化
   * @param exclusive  是否排他
   * @param autoDelete 是否自动删除
   */
  public void declareQueue(String queueName, boolean durable, boolean exclusive, boolean autoDelete) {
    final RabbitMQClient client = Future.await(channelPool.borrowClient());
    try {
      Future.await(client.queueDeclare(queueName, durable, exclusive, autoDelete));
      log.info("队列声明成功 - 名称: {}, 持久化: {}, 排他: {}, 自动删除: {}",
          queueName, durable, exclusive, autoDelete);
    } catch (Exception cause) {
      log.error("声明队列失败 - 名称: {}", queueName, cause);
      throw new RuntimeException("声明队列失败", cause);
    } finally {
      Future.await(channelPool.returnClient(client));
    }
  }

  /**
   * 绑定队列到交换机
   *
   * @param queueName    队列名称
   * @param exchangeName 交换机名称
   * @param routingKey   路由键
   */
  public void bindQueue(String queueName, String exchangeName, String routingKey) {
    final RabbitMQClient client = Future.await(channelPool.borrowClient());
    try {
      Future.await(client.queueBind(queueName, exchangeName, routingKey));
      log.info("队列绑定成功 - 队列: {}, 交换机: {}, 路由键: {}", queueName, exchangeName, routingKey);
    } catch (Exception cause) {
      log.error("绑定队列失败 - 队列: {}, 交换机: {}, 路由键: {}", queueName, exchangeName, routingKey, cause);
      throw new RuntimeException("绑定队列失败", cause);
    } finally {
      Future.await(channelPool.returnClient(client));
    }
  }

  /**
   * 删除队列
   *
   * @param queueName 队列名称
   */
  public void deleteQueue(String queueName) {
    final RabbitMQClient client = Future.await(channelPool.borrowClient());
    try {
      Future.await(client.queueDelete(queueName));
      log.info("队列删除成功 - 名称: {}", queueName);
    } catch (Exception cause) {
      log.error("删除队列失败 - 名称: {}", queueName, cause);
      throw new RuntimeException("删除队列失败", cause);
    } finally {
      Future.await(channelPool.returnClient(client));
    }
  }

  /**
   * 删除交换机
   *
   * @param exchangeName 交换机名称
   */
  public void deleteExchange(String exchangeName) {
    final RabbitMQClient client = Future.await(channelPool.borrowClient());
    try {
      Future.await(client.exchangeDelete(exchangeName));
      log.info("交换机删除成功 - 名称: {}", exchangeName);
    } catch (Exception cause) {
      log.error("删除交换机失败 - 名称: {}", exchangeName, cause);
      throw new RuntimeException("删除交换机失败", cause);
    } finally {
      Future.await(channelPool.returnClient(client));
    }
  }

  /**
   * 创建JSON消息属性
   *
   * @return JSON消息属性
   */
  private JsonObject createJsonProperties() {
    return new JsonObject()
        .put("contentType", "application/json")
        .put("contentEncoding", "UTF-8");
  }
}
