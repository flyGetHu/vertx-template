package com.vertx.template.mq.consumer;

import com.google.inject.Injector;
import com.vertx.template.mq.consumer.annotation.RabbitConsumer;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.reflections.Reflections;

/** 消费者注册器 负责自动扫描、注册和管理消费者的生命周期 */
@Slf4j
@Singleton
public class ConsumerRegistry {

  private final Injector injector;
  private final ConsumerManager consumerManager;
  private final ConcurrentMap<String, MessageConsumer> registeredConsumers =
      new ConcurrentHashMap<>();
  private final Map<String, RabbitConsumer> consumerAnnotations = new ConcurrentHashMap<>();

  /**
   * 构造器
   *
   * @param injector Guice注入器
   * @param consumerManager 消费者管理器
   */
  @Inject
  public ConsumerRegistry(Injector injector, ConsumerManager consumerManager) {
    this.injector = injector;
    this.consumerManager = consumerManager;
  }

  /**
   * 自动扫描并注册所有消费者
   *
   * @param basePackage 扫描的基础包路径
   */
  public void scanAndRegisterConsumers(String basePackage) {
    log.info("开始扫描消费者，基础包: {}", basePackage);

    try {
      final Reflections reflections = new Reflections(basePackage);
      final Set<Class<?>> consumerClasses = reflections.getTypesAnnotatedWith(RabbitConsumer.class);

      if (consumerClasses.isEmpty()) {
        log.info("未找到消费者类，包路径: {}", basePackage);
        return;
      }

      log.info("找到 {} 个消费者类", consumerClasses.size());

      for (final Class<?> consumerClass : consumerClasses) {
        registerConsumer(consumerClass);
      }

      log.info("消费者扫描和注册完成，共注册 {} 个消费者", registeredConsumers.size());
    } catch (Exception e) {
      log.error("扫描消费者失败", e);
      throw new RuntimeException("扫描消费者失败", e);
    }
  }

  /**
   * 注册单个消费者
   *
   * @param consumerClass 消费者类
   */
  public void registerConsumer(Class<?> consumerClass) {
    try {
      if (!MessageConsumer.class.isAssignableFrom(consumerClass)) {
        log.warn("类 {} 未实现 MessageConsumer 接口，跳过注册", consumerClass.getName());
        return;
      }

      final RabbitConsumer annotation = consumerClass.getAnnotation(RabbitConsumer.class);
      if (annotation == null) {
        log.warn("类 {} 未标注 @RabbitConsumer 注解，跳过注册", consumerClass.getName());
        return;
      }

      // 创建消费者实例
      final MessageConsumer consumer =
          (MessageConsumer) consumerClass.getDeclaredConstructor().newInstance();
      final String consumerName = consumer.getConsumerName();

      if (registeredConsumers.containsKey(consumerName)) {
        log.warn("消费者 {} 已存在，跳过重复注册", consumerName);
        return;
      }

      // 注册消费者
      registeredConsumers.put(consumerName, consumer);
      consumerAnnotations.put(consumerName, annotation);

      log.info("注册消费者成功: {} -> {}", consumerName, consumerClass.getName());

      // 如果消费者已启用，立即启动
      if (annotation.enabled()) {
        consumerManager.startConsumer(consumer, annotation);
      } else {
        log.info("消费者 {} 已禁用，不会自动启动", consumerName);
      }

    } catch (InstantiationException | IllegalAccessException | NoSuchMethodException e) {
      log.error("创建消费者实例失败: {}", consumerClass.getName(), e);
      throw new RuntimeException("创建消费者实例失败: " + consumerClass.getName(), e);
    } catch (Exception cause) {
      log.error("注册消费者失败: {}", consumerClass.getName(), cause);
      throw new RuntimeException("注册消费者失败: " + consumerClass.getName(), cause);
    }
  }

  /**
   * 注销消费者
   *
   * @param consumerName 消费者名称
   */
  public void unregisterConsumer(String consumerName) {
    log.info("注销消费者: {}", consumerName);

    try {
      // 先停止消费者
      if (consumerManager.isConsumerActive(consumerName)) {
        consumerManager.stopConsumer(consumerName);
      }

      // 移除注册信息
      final MessageConsumer consumer = registeredConsumers.remove(consumerName);
      consumerAnnotations.remove(consumerName);

      if (consumer != null) {
        log.info("消费者 {} 注销成功", consumerName);
      } else {
        log.warn("消费者 {} 未找到，可能已经注销", consumerName);
      }

    } catch (Exception cause) {
      log.error("注销消费者 {} 失败", consumerName, cause);
      throw new RuntimeException("注销消费者失败: " + consumerName, cause);
    }
  }

  /** 停止所有注册的消费者 */
  public void stopAllConsumers() {
    log.info("正在停止所有注册的消费者...");

    try {
      // 先调用所有消费者的onStop回调
      for (final MessageConsumer consumer : registeredConsumers.values()) {
        try {
          consumer.onStop();
        } catch (Exception cause) {
          log.error("消费者 {} 停止回调失败", consumer.getConsumerName(), cause);
        }
      }

      // 然后停止消费者管理器中的所有消费者
      consumerManager.stopAllConsumers();

      registeredConsumers.clear();
      consumerAnnotations.clear();
      log.info("所有注册的消费者已停止");

    } catch (Exception cause) {
      log.error("停止所有消费者失败", cause);
      throw new RuntimeException("停止所有消费者失败", cause);
    }
  }

  /**
   * 启动指定的消费者
   *
   * @param consumerName 消费者名称
   */
  public void startConsumer(String consumerName) {
    final MessageConsumer consumer = registeredConsumers.get(consumerName);
    final RabbitConsumer annotation = consumerAnnotations.get(consumerName);

    if (consumer == null || annotation == null) {
      throw new IllegalArgumentException("消费者未注册: " + consumerName);
    }

    if (consumerManager.isConsumerActive(consumerName)) {
      log.warn("消费者 {} 已经在运行中", consumerName);
      return;
    }

    try {
      consumerManager.startConsumer(consumer, annotation);
      log.info("消费者 {} 启动成功", consumerName);
    } catch (Exception cause) {
      log.error("启动消费者 {} 失败", consumerName, cause);
      throw new RuntimeException("启动消费者失败: " + consumerName, cause);
    }
  }

  /**
   * 停止指定的消费者
   *
   * @param consumerName 消费者名称
   */
  public void stopConsumer(String consumerName) {
    if (!consumerManager.isConsumerActive(consumerName)) {
      log.warn("消费者 {} 未运行", consumerName);
      return;
    }

    try {
      consumerManager.stopConsumer(consumerName);
      log.info("消费者 {} 停止成功", consumerName);
    } catch (Exception cause) {
      log.error("停止消费者 {} 失败", consumerName, cause);
      throw new RuntimeException("停止消费者失败: " + consumerName, cause);
    }
  }

  /**
   * 重启指定的消费者
   *
   * @param consumerName 消费者名称
   */
  public void restartConsumer(String consumerName) {
    log.info("重启消费者: {}", consumerName);

    try {
      // 先停止
      if (consumerManager.isConsumerActive(consumerName)) {
        stopConsumer(consumerName);
        // 等待一段时间确保完全停止
        Thread.sleep(1000);
      }

      // 再启动
      startConsumer(consumerName);

      log.info("消费者 {} 重启成功", consumerName);
    } catch (Exception cause) {
      log.error("重启消费者 {} 失败", consumerName, cause);
      throw new RuntimeException("重启消费者失败: " + consumerName, cause);
    }
  }

  /**
   * 获取已注册消费者数量
   *
   * @return 已注册消费者数量
   */
  public int getRegisteredConsumerCount() {
    return registeredConsumers.size();
  }

  /**
   * 获取活跃消费者数量
   *
   * @return 活跃消费者数量
   */
  public int getActiveConsumerCount() {
    return consumerManager.getActiveConsumerCount();
  }

  /**
   * 检查消费者是否已注册
   *
   * @param consumerName 消费者名称
   * @return 是否已注册
   */
  public boolean isConsumerRegistered(String consumerName) {
    return registeredConsumers.containsKey(consumerName);
  }

  /**
   * 检查消费者是否活跃
   *
   * @param consumerName 消费者名称
   * @return 是否活跃
   */
  public boolean isConsumerActive(String consumerName) {
    return consumerManager.isConsumerActive(consumerName);
  }

  /**
   * 获取已注册的消费者实例
   *
   * @param consumerName 消费者名称
   * @return 消费者实例，如果未注册则返回null
   */
  public MessageConsumer getRegisteredConsumer(String consumerName) {
    return registeredConsumers.get(consumerName);
  }
}
