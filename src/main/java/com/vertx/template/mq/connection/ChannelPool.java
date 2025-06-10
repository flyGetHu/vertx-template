package com.vertx.template.mq.connection;

import com.vertx.template.mq.config.RabbitMqConfig;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.rabbitmq.RabbitMQClient;
import lombok.extern.slf4j.Slf4j;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * RabbitMQ通道池
 * 维护发送消息用的RabbitMQClient池，支持客户端的借用和归还
 */
@Slf4j
@Singleton
public class ChannelPool {

  /** 默认池大小 */
  private static final int DEFAULT_POOL_SIZE = 10;
  /** 默认最大池大小 */
  private static final int DEFAULT_MAX_POOL_SIZE = 50;

  private final Vertx vertx;
  private final RabbitMqConnectionManager connectionManager;
  private final AtomicBoolean initialized = new AtomicBoolean(false);
  private final AtomicInteger currentPoolSize = new AtomicInteger(0);

  private BlockingQueue<RabbitMQClient> availableClients;
  private int poolSize = DEFAULT_POOL_SIZE;
  private int maxPoolSize = DEFAULT_MAX_POOL_SIZE;

  /**
   * 构造器
   *
   * @param vertx             Vert.x实例
   * @param connectionManager 连接管理器
   */
  @Inject
  public ChannelPool(Vertx vertx, RabbitMqConnectionManager connectionManager) {
    this.vertx = vertx;
    this.connectionManager = connectionManager;
  }

  /**
   * 初始化通道池
   */
  public void initialize() {
    initialize(DEFAULT_POOL_SIZE, DEFAULT_MAX_POOL_SIZE);
  }

  /**
   * 初始化通道池
   *
   * @param poolSize    初始池大小
   * @param maxPoolSize 最大池大小
   */
  public void initialize(int poolSize, int maxPoolSize) {
    if (initialized.get()) {
      return;
    }

    this.poolSize = poolSize;
    this.maxPoolSize = maxPoolSize;
    this.availableClients = new LinkedBlockingQueue<>(maxPoolSize);

    log.info("正在初始化通道池，初始大小: {}，最大大小: {}", poolSize, maxPoolSize);

    try {
      // 预创建客户端
      for (int i = 0; i < poolSize; i++) {
        final RabbitMQClient client = createClient();
        if (client != null) {
          availableClients.offer(client);
          currentPoolSize.incrementAndGet();
        }
      }

      initialized.set(true);
      log.info("通道池初始化完成，当前池大小: {}", currentPoolSize.get());
    } catch (Exception cause) {
      log.error("通道池初始化失败", cause);
      throw new RuntimeException("通道池初始化失败", cause);
    }
  }

  /**
   * 从池中获取客户端
   *
   * @return 客户端实例
   */
  public RabbitMQClient borrowClient() {
    if (!initialized.get()) {
      throw new IllegalStateException("通道池尚未初始化");
    }

    final RabbitMQClient client = availableClients.poll();

    if (client == null || !client.isConnected()) {
      // 池中没有可用客户端或客户端已断连，创建新客户端
      if (currentPoolSize.get() < maxPoolSize) {
        final RabbitMQClient newClient = createClient();
        if (newClient != null) {
          currentPoolSize.incrementAndGet();
          log.debug("创建新客户端，当前池大小: {}", currentPoolSize.get());

          try {
            // 启动新客户端
            Future.await(newClient.start());
            return newClient;
          } catch (Exception cause) {
            log.error("启动新客户端失败", cause);
            currentPoolSize.decrementAndGet();
            throw new RuntimeException("启动新客户端失败", cause);
          }
        }
      } else {
        throw new RuntimeException("通道池已满，无法创建新客户端");
      }
    }

    if (client != null && client.isConnected()) {
      log.debug("成功获取客户端");
      return client;
    } else {
      throw new RuntimeException("无法获取有效客户端");
    }
  }

  /**
   * 归还客户端到池中
   *
   * @param client 要归还的客户端
   */
  public void returnClient(RabbitMQClient client) {
    if (!initialized.get()) {
      throw new IllegalStateException("通道池尚未初始化");
    }

    try {
      if (client != null && client.isConnected()) {
        final boolean offered = availableClients.offer(client);
        if (offered) {
          log.debug("客户端归还成功");
        } else {
          // 池已满，关闭客户端
          Future.await(client.stop());
          currentPoolSize.decrementAndGet();
          log.debug("池已满，关闭客户端，当前池大小: {}", currentPoolSize.get());
        }
      } else {
        // 客户端已关闭，减少计数
        if (client != null) {
          currentPoolSize.decrementAndGet();
          log.debug("归还的客户端已关闭，当前池大小: {}", currentPoolSize.get());
        }
      }
    } catch (Exception cause) {
      log.error("归还客户端失败", cause);
      throw new RuntimeException("归还客户端失败", cause);
    }
  }

  /**
   * 关闭通道池
   */
  public void close() {
    log.info("正在关闭通道池...");

    try {
      // 关闭所有客户端
      RabbitMQClient client;
      while ((client = availableClients.poll()) != null) {
        try {
          if (client.isConnected()) {
            Future.await(client.stop()); // 同步关闭
          }
        } catch (Exception e) {
          log.warn("关闭客户端时发生异常: {}", e.getMessage());
        }
      }

      currentPoolSize.set(0);
      initialized.set(false);
      log.info("通道池已关闭");

    } catch (Exception e) {
      log.error("关闭通道池失败", e);
      throw new RuntimeException("关闭通道池失败", e);
    }
  }

  /**
   * 获取池状态信息
   *
   * @return 池状态字符串
   */
  public String getPoolStats() {
    return String.format("通道池状态 - 可用: %d, 总计: %d, 最大: %d",
        availableClients.size(), currentPoolSize.get(), maxPoolSize);
  }

  /**
   * 获取可用客户端数量
   *
   * @return 可用客户端数量
   */
  public int getAvailableClientCount() {
    return availableClients.size();
  }

  /**
   * 获取当前池大小
   *
   * @return 当前池大小
   */
  public int getCurrentPoolSize() {
    return currentPoolSize.get();
  }

  /**
   * 创建新的RabbitMQ客户端
   *
   * @return 新的客户端实例
   */
  private RabbitMQClient createClient() {
    try {
      return connectionManager.getClient();
    } catch (Exception e) {
      log.error("创建客户端失败", e);
      return null;
    }
  }
}
