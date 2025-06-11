package com.vertx.template.mq.connection;

import com.vertx.template.mq.config.ChannelPoolConfig;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.rabbitmq.RabbitMQClient;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * 简化的RabbitMQ连接池
 *
 * 核心功能：
 * - 连接池管理（借用/归还）
 * - 基本的连接验证
 * - 线程安全操作
 * - 优雅关闭
 */
@Slf4j
@Singleton
public class ChannelPool {

  private final Vertx vertx;
  private final RabbitMqConnectionManager connectionManager;
  private final ChannelPoolConfig config;

  // 连接池状态
  private final AtomicBoolean initialized = new AtomicBoolean(false);
  private final AtomicBoolean shutdown = new AtomicBoolean(false);
  private final AtomicInteger totalConnections = new AtomicInteger(0);

  // 连接存储 - 使用简单的队列
  private final ConcurrentLinkedQueue<RabbitMQClient> availableClients = new ConcurrentLinkedQueue<>();

  // 清理任务ID
  private Long cleanupTimerId;

  @Inject
  public ChannelPool(final Vertx vertx, final RabbitMqConnectionManager connectionManager,
      final ChannelPoolConfig config) {
    this.vertx = vertx;
    this.connectionManager = connectionManager;
    this.config = config;
  }

  /**
   * 初始化连接池
   */
  public void initialize() {
    if (!initialized.compareAndSet(false, true)) {
      log.warn("连接池已经初始化，跳过重复初始化");
      return;
    }

    if (!config.isValid()) {
      throw new IllegalArgumentException("连接池配置无效: " + config);
    }

    log.info("初始化连接池 - 初始大小: {}, 最大大小: {}", config.getInitialSize(), config.getMaxSize());

    try {
      // 预创建初始连接
      createInitialConnections();

      // 启动定期清理任务
      startCleanupTask();

      log.info("连接池初始化完成 - 总连接数: {}, 可用连接: {}",
          totalConnections.get(), availableClients.size());
    } catch (Exception e) {
      initialized.set(false);
      log.error("连接池初始化失败", e);
      throw new RuntimeException("连接池初始化失败", e);
    }
  }

  /**
   * 借用客户端
   */
  public RabbitMQClient borrowClient() {
    checkState();

    // 尝试从池中获取可用连接
    RabbitMQClient client = getHealthyClient();
    if (client != null) {
      return client;
    }

    // 池中没有可用连接，尝试创建新连接
    client = createNewClient();
    if (client != null) {
      return client;
    }

    throw new RuntimeException("无法获取连接：连接池已满且无法创建新连接");
  }

  /**
   * 归还客户端
   */
  public void returnClient(final RabbitMQClient client) {
    if (!initialized.get() || client == null) {
      closeClientSafely(client);
      return;
    }

    // 检查连接是否仍然有效
    if (isClientValid(client)) {
      availableClients.offer(client);
      log.debug("客户端归还成功");
    } else {
      closeClientAndDecrement(client);
      log.debug("归还的客户端无效，已关闭");
    }
  }

  /**
   * 优雅关闭
   */
  public void shutdown() {
    if (!shutdown.compareAndSet(false, true)) {
      return;
    }

    log.info("开始关闭连接池");

    try {
      // 停止清理任务
      if (cleanupTimerId != null) {
        vertx.cancelTimer(cleanupTimerId);
      }

      // 关闭所有连接
      closeAllConnections();

      // 清理状态
      availableClients.clear();
      totalConnections.set(0);

      log.info("连接池已关闭");
    } catch (Exception e) {
      log.error("关闭连接池失败", e);
    } finally {
      initialized.set(false);
    }
  }

  /**
   * 获取池状态
   */
  public String getPoolStats() {
    return String.format("连接池状态 - 可用: %d, 总计: %d, 最大: %d, 已初始化: %s",
        availableClients.size(), totalConnections.get(), config.getMaxSize(), initialized.get());
  }

  /**
   * 获取可用连接数
   */
  public int getAvailableCount() {
    return availableClients.size();
  }

  /**
   * 获取总连接数
   */
  public int getTotalConnections() {
    return totalConnections.get();
  }

  // ================================
  // 私有方法
  // ================================

  private void checkState() {
    if (!initialized.get()) {
      throw new IllegalStateException("连接池尚未初始化");
    }
    if (shutdown.get()) {
      throw new IllegalStateException("连接池已关闭");
    }
  }

  /**
   * 创建初始连接
   */
  private void createInitialConnections() {
    final int initialSize = config.getInitialSize();
    int created = 0;

    for (int i = 0; i < initialSize; i++) {
      try {
        final RabbitMQClient client = connectionManager.getClient();
        if (client != null) {
          availableClients.offer(client);
          totalConnections.incrementAndGet();
          created++;
        }
      } catch (Exception e) {
        log.warn("创建初始连接失败，索引: {}", i, e);
      }
    }

    log.info("创建初始连接完成: {}/{}", created, initialSize);
  }

  /**
   * 获取健康的客户端
   */
  private RabbitMQClient getHealthyClient() {
    RabbitMQClient client;
    while ((client = availableClients.poll()) != null) {
      if (isClientValid(client)) {
        return client;
      } else {
        // 连接无效，关闭并继续
        closeClientAndDecrement(client);
      }
    }
    return null;
  }

  /**
   * 创建新客户端
   */
  private RabbitMQClient createNewClient() {
    final int current = totalConnections.get();
    if (current >= config.getMaxSize()) {
      return null;
    }

    try {
      final RabbitMQClient client = connectionManager.getClient();
      if (client != null) {
        totalConnections.incrementAndGet();
        return client;
      }
    } catch (Exception e) {
      log.error("创建新连接失败", e);
    }

    return null;
  }

  /**
   * 检查客户端是否有效
   */
  private boolean isClientValid(final RabbitMQClient client) {
    return client != null && client.isConnected();
  }

  /**
   * 关闭客户端并减少计数
   */
  private void closeClientAndDecrement(final RabbitMQClient client) {
    closeClientSafely(client);
    totalConnections.decrementAndGet();
  }

  /**
   * 安全关闭客户端
   */
  private void closeClientSafely(final RabbitMQClient client) {
    if (client != null) {
      try {
        if (client.isConnected()) {
          Future.await(client.stop());
        }
      } catch (Exception e) {
        log.debug("关闭客户端时发生异常", e);
      }
    }
  }

  /**
   * 关闭所有连接
   */
  private void closeAllConnections() {
    RabbitMQClient client;
    while ((client = availableClients.poll()) != null) {
      closeClientSafely(client);
    }
  }

  /**
   * 启动清理任务
   */
  private void startCleanupTask() {
    // 每分钟检查一次无效连接
    cleanupTimerId = vertx.setPeriodic(60_000, id -> cleanupInvalidConnections());
    log.debug("清理任务已启动");
  }

  /**
   * 清理无效连接
   */
  private void cleanupInvalidConnections() {
    if (shutdown.get()) {
      return;
    }

    try {
      final int sizeBefore = availableClients.size();
      int removed = 0;

      // 检查并移除无效连接
      final int checkCount = Math.min(availableClients.size(), 5); // 每次最多检查5个
      for (int i = 0; i < checkCount; i++) {
        final RabbitMQClient client = availableClients.poll();
        if (client == null) {
          break;
        }

        if (isClientValid(client)) {
          // 连接有效，放回池中
          availableClients.offer(client);
        } else {
          // 连接无效，关闭
          closeClientAndDecrement(client);
          removed++;
        }
      }

      if (removed > 0) {
        log.info("清理无效连接: {}, 剩余可用: {}", removed, availableClients.size());
      }
    } catch (Exception e) {
      log.error("清理连接失败", e);
    }
  }
}
