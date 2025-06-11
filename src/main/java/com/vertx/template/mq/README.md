# RabbitMQ 消息队列模块 - 架构设计与使用指南

## 📋 模块概述

基于 Vert.x 的企业级 RabbitMQ 消息队列解决方案，提供完整的消息生产、消费、监控和故障恢复能力。

### 🎯 核心特性

- **📡 统一消息管理**：生产者和消费者的统一管理接口
- **🔄 智能重试机制**：多层次重试策略，包括消息级和连接级重试
- **⚡ 熔断保护**：避免雪崩效应的自动熔断机制
- **🩺 健康监控**：实时健康检查和自动故障恢复
- **🏊 连接池管理**：高性能的连接池设计
- **📊 全面监控**：详细的统计信息和状态监控
- **🔌 故障隔离**：消费者独立连接，避免相互影响

## 🏗️ 架构设计

### 总体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                        MQManager                            │
│  ┌─────────────────┐              ┌─────────────────────┐   │
│  │   消费者管理     │              │    生产者管理       │   │
│  │                │              │                     │   │
│  │ • 自动扫描注册   │              │ • 连接池管理        │   │
│  │ • 独立连接管理   │              │ • 消息发送API       │   │
│  │ • 健康检查      │              │ • 故障转移          │   │
│  └─────────────────┘              └─────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
        │                                        │
        ▼                                        ▼
┌─────────────────┐                    ┌─────────────────────┐
│   监控与重试     │                    │     连接池          │
│                │                    │                     │
│ • BasicMonitor  │                    │ • 连接复用          │
│ • RetryManager  │                    │ • 健康检查          │
│ • 熔断器机制     │                    │ • 动态扩缩          │
└─────────────────┘                    └─────────────────────┘
        │                                        │
        ▼                                        ▼
┌─────────────────────────────────────────────────────────────┐
│                     RabbitMQ Cluster                       │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │   Queue-1   │  │   Queue-2   │  │    Exchange        │ │
│  └─────────────┘  └─────────────┘  └─────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 关键组件说明

| 组件                     | 职责         | 特性                      |
| ------------------------ | ------------ | ------------------------- |
| **MQManager**            | 统一管理入口 | 消费者/生产者生命周期管理 |
| **BasicConsumerMonitor** | 性能监控     | 实时统计、成功率计算      |
| **ConsumerRetryManager** | 重试与熔断   | 指数退避、熔断保护        |
| **ChannelPool**          | 连接池管理   | 连接复用、资源优化        |
| **@RabbitConsumer**      | 声明式配置   | 注解驱动的消费者配置      |

## 🔧 配置管理

### 基础配置结构

```yaml
# config.yml
mq:
  rabbitmq:
    enabled: true
    host: localhost
    port: 5672
    user: guest
    password: guest
    virtual_host: /

    # 连接配置
    connection_timeout: 60000
    requested_heartbeat: 60
    network_recovery_interval: 5000
    automatic_recovery_enabled: true

    # 连接池配置
    pool:
      initial_size: 5      # 初始连接数
      max_size: 20         # 最大连接数
      validation_query_timeout: 5000
      cleanup_interval: 300000
```

### 高级配置选项

```yaml
mq:
  rabbitmq:
    # 重试配置
    retry:
      max_attempts: 15           # 最大重试次数
      initial_interval: 1000     # 初始重试间隔(ms)
      max_interval: 60000        # 最大重试间隔(ms)
      backoff_multiplier: 1.5    # 退避倍数

    # 熔断器配置
    circuit_breaker:
      failure_threshold: 5       # 失败阈值
      timeout: 300000           # 熔断超时(ms)

    # 健康检查配置
    health_check:
      interval: 30000           # 检查间隔(ms)
      enabled: true             # 是否启用
```

## 🔄 消费者设计

### 消费者注解配置

```java
@RabbitConsumer(
    queueName = "user.events",          // 队列名称
    enabled = true,                     // 是否启用
    autoAck = false,                    // 手动确认(推荐)
    maxRetries = 3,                     // 消息级重试次数
    retryDelayMs = 1000L,              // 重试延迟
    prefetchCount = 20,                 // 预取消息数
    description = "用户事件处理",        // 描述信息
    autoReconnect = true,               // 自动重连
    healthCheckInterval = 30000L        // 健康检查间隔
)
@Singleton
public class UserEventConsumer implements MessageConsumer {

    @Override
    public String getConsumerName() {
        return "user-event-consumer";
    }

    @Override
    public Boolean handleMessage(final RabbitMQMessage message) {
        try {
            final String payload = message.body().toString();
            log.info("处理用户事件: {}", payload);

            // 业务逻辑处理
            processUserEvent(payload);

            return true;  // 处理成功
        } catch (RetryableException e) {
            log.warn("可重试异常: {}", e.getMessage());
            return false; // 触发重试
        } catch (Exception e) {
            log.error("不可重试异常", e);
            // 记录到死信队列或日志系统
            recordFailedMessage(message, e);
            return true;  // 避免无意义重试
        }
    }

    @Override
    public void onStart() {
        log.info("消费者启动: {}", getConsumerName());
        // 初始化资源
    }

    @Override
    public void onStop() {
        log.info("消费者停止: {}", getConsumerName());
        // 清理资源
    }

    @Override
    public void onMessageFailed(final RabbitMQMessage message, final Throwable cause) {
        log.error("消息处理失败: {}", message.body().toString(), cause);
        // 失败处理逻辑
    }
}
```

### 消费者生命周期管理

```java
// 自动扫描并启动所有消费者
mqManager.scanAndStartConsumers("com.vertx.template.consumer");

// 手动控制单个消费者
mqManager.startConsumer("user-event-consumer");
mqManager.stopConsumer("user-event-consumer");

// 查询消费者状态
boolean isActive = mqManager.isConsumerActive("user-event-consumer");
boolean isRegistered = mqManager.isConsumerRegistered("user-event-consumer");

// 获取统计信息
final int activeCount = mqManager.getActiveConsumerCount();
final int registeredCount = mqManager.getRegisteredConsumerCount();
```

## 🔄 智能重试策略

### 三层重试机制

#### 1. 消息级重试（Message-Level Retry）

```java
// 基于返回值的重试逻辑
@Override
public Boolean handleMessage(final RabbitMQMessage message) {
    try {
        processMessage(message);
        return true;   // 成功，消息确认
    } catch (Exception e) {
        return false;  // 失败，触发重试
    }
}
```

**重试策略**：
- **手动确认模式**：使用 `basicNack` + `requeue=true` 重新入队
- **自动确认模式**：延迟重新处理，线性延迟递增
- **重试限制**：基于 `@RabbitConsumer.maxRetries()` 配置
- **延迟策略**：每次重试延迟递增，避免雷群效应

#### 2. 连接级重试（Connection-Level Retry）

```java
// ConsumerRetryManager 的指数退避重试
public boolean scheduleRetry(final String consumerName, final Runnable retryAction) {
    // 指数退避算法
    final long retryInterval = calculateRetryInterval(retryCount);

    // 调度重试任务
    vertx.setTimer(retryInterval, id -> {
        try {
            retryAction.run();
            recordSuccess(consumerName);  // 重置重试状态
        } catch (Exception e) {
            // 继续重试或触发熔断
            handleRetryFailure(consumerName, e);
        }
    });
}

// 指数退避算法实现
private long calculateRetryInterval(final int retryAttempt) {
    final long baseInterval = initialRetryInterval * (long) Math.pow(backoffMultiplier, retryAttempt);
    final long cappedInterval = Math.min(baseInterval, maxRetryInterval);

    // 添加抖动，避免雷群效应
    final long jitter = (long) (cappedInterval * 0.1 * Math.random());
    return cappedInterval + jitter;
}
```

**重试配置**：
- **最大重试次数**：15次（可配置）
- **初始延迟**：1秒
- **最大延迟**：60秒
- **退避倍数**：1.5
- **抖动机制**：±10% 随机延迟

#### 3. 健康检查重试（Health-Check Retry）

```java
// 定期健康检查触发的重连
private void performHealthCheck() {
    for (final String consumerName : registeredConsumers.keySet()) {
        if (!isConsumerConnected(consumerName)) {
            log.warn("检测到消费者 {} 连接断开，触发重连", consumerName);
            triggerConsumerReconnect(consumerName);
        }
    }
}

// 轻量级健康检查
private boolean isConsumerHealthy(final String consumerName, final RabbitMQClient client) {
    try {
        // 只检查客户端连接状态，避免创建任何新资源
        return client.isConnected();
    } catch (Exception e) {
        return false;
    }
}
```

## ⚡ 熔断器设计

### 熔断器状态机

```
     失败次数 < 阈值
    ┌─────────────────┐
    │     CLOSED      │ ──────┐
    │   (正常状态)     │       │ 连续失败 >= 5次
    └─────────────────┘       │
             ▲                │
             │                ▼
    超时后尝试重置     ┌─────────────────┐
             │        │      OPEN       │
             │        │   (熔断状态)     │
    ┌─────────────────┐└─────────────────┘
    │   HALF_OPEN     │
    │  (半开状态)      │
    └─────────────────┘
```

### 熔断器实现

```java
@Data
public static class CircuitBreakerState {
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private volatile LocalDateTime circuitOpenTime;
    private volatile boolean circuitOpen = false;

    // 记录失败，检查是否触发熔断
    public void recordFailure() {
        consecutiveFailures.incrementAndGet();
    }

    // 记录成功，重置熔断器
    public void recordSuccess() {
        consecutiveFailures.set(0);
        circuitOpen = false;
        circuitOpenTime = null;
    }

    // 检查是否应该开启熔断器
    public boolean shouldTripCircuit(final int threshold) {
        return consecutiveFailures.get() >= threshold;
    }

    // 开启熔断器
    public void openCircuit() {
        circuitOpen = true;
        circuitOpenTime = LocalDateTime.now();
    }

    // 检查是否可以尝试重置
    public boolean canAttemptReset(final long timeoutMs) {
        if (!circuitOpen || circuitOpenTime == null) {
            return true;
        }

        final long elapsed = Duration.between(circuitOpenTime, LocalDateTime.now()).toMillis();
        return elapsed >= timeoutMs;
    }
}
```

### 熔断器配置与行为

| 参数         | 默认值 | 说明                             |
| ------------ | ------ | -------------------------------- |
| **失败阈值** | 5次    | 连续失败次数达到此值时开启熔断器 |
| **熔断超时** | 5分钟  | 熔断器开启后的冷却时间           |
| **半开状态** | 自动   | 超时后自动进入半开状态尝试恢复   |

## 🩺 健康检查与监控

### 健康检查机制

```java
// 启动定期健康检查
private void startConsumerHealthCheck() {
    healthCheckTimerId = vertx.setPeriodic(30000L, id -> {
        performHealthCheck();
    });
}

// 执行健康检查
private void performHealthCheck() {
    // 只检查活跃的消费者（已启动且启用的）
    for (final String consumerName : activeConsumers.keySet()) {
        final RabbitConsumer annotation = consumerAnnotations.get(consumerName);

        // 三重验证：注解存在 + 消费者启用 + 自动重连启用
        if (annotation != null && annotation.enabled() && annotation.autoReconnect()) {
            if (!isConsumerConnected(consumerName)) {
                triggerConsumerReconnect(consumerName);
            }
        }
    }
}
```

### 监控统计信息

```java
// 获取详细统计信息
final String stats = mqManager.getMonitor().getStatsString();
/*
输出示例：
消费者统计:
  user-event-consumer: 成功=1250, 失败=45, 重试=23, 重连=2, 断连=1, 成功率=96.5%
  order-event-consumer: 成功=890, 失败=12, 重试=8, 重连=0, 断连=0, 成功率=98.7%
*/

// 获取单个消费者统计
final ConsumerStats userStats = monitor.getConsumerStats("user-event-consumer");
final double successRate = userStats.getSuccessRate();
final long totalMessages = userStats.getTotalCount();
```

### 监控指标说明

| 指标             | 说明                      | 用途           |
| ---------------- | ------------------------- | -------------- |
| **成功次数**     | 消息处理成功的总数        | 性能基准       |
| **失败次数**     | 消息处理失败的总数        | 错误率分析     |
| **重试次数**     | 消息级重试的总数          | 重试效果评估   |
| **重连次数**     | 连接级重连的总数          | 网络稳定性指标 |
| **断连次数**     | 检测到的断连总数          | 连接质量评估   |
| **成功率**       | 成功次数/(成功+失败)×100% | 核心KPI        |
| **最后活跃时间** | 最近一次消息处理时间      | 消费者活跃度   |

## 📡 消息发送API

### 基础发送方法

```java
@Inject
private MQManager mqManager;

// 1. 发送文本消息到队列
mqManager.sendToQueue("user.notifications", "用户注册成功");

// 2. 发送JSON消息到队列
final JsonObject userData = new JsonObject()
    .put("userId", "12345")
    .put("action", "LOGIN")
    .put("timestamp", System.currentTimeMillis());
mqManager.sendJsonToQueue("user.events", userData);

// 3. 发送消息到交换机
mqManager.sendToExchange("user.exchange", "user.created", "用户创建事件");

// 4. 发送JSON到交换机
mqManager.sendJsonToExchange("event.exchange", "order.completed", orderData);

// 5. 带属性的消息发送
final JsonObject properties = new JsonObject()
    .put("priority", 5)
    .put("expiration", "30000")
    .put("message-id", UUID.randomUUID().toString());
mqManager.sendToQueue("priority.queue", message, properties);
```

### 发送消息的异常处理

```java
try {
    mqManager.sendToQueue("user.events", userEvent);
    log.info("消息发送成功");
} catch (RuntimeException e) {
    if (e.getMessage().contains("队列")) {
        log.error("队列不存在，请检查RabbitMQ配置: {}", e.getMessage());
        // 处理队列不存在的情况
    } else {
        log.error("消息发送失败", e);
        // 处理其他发送失败情况
    }
}
```

## 🏊 连接池设计

### 连接池架构

```java
// 连接池关键特性
public class ChannelPool {
    private final ConcurrentLinkedQueue<RabbitMQClient> availableClients;
    private final AtomicInteger totalConnections;

    // 借用连接
    public RabbitMQClient borrowClient() {
        // 1. 从池中获取健康连接
        RabbitMQClient client = getHealthyClient();
        if (client != null) return client;

        // 2. 池中无可用连接，创建新连接
        if (totalConnections.get() < config.getMaxSize()) {
            return createNewClient();
        }

        throw new RuntimeException("连接池已满");
    }

    // 归还连接
    public void returnClient(final RabbitMQClient client) {
        if (isClientValid(client)) {
            availableClients.offer(client);
        } else {
            closeClientAndDecrement(client);
        }
    }
}
```

### 连接池配置策略

| 场景           | 初始连接数 | 最大连接数 | 适用情况               |
| -------------- | ---------- | ---------- | ---------------------- |
| **小型应用**   | 2-5        | 10-20      | 低并发，偶尔发送消息   |
| **中型应用**   | 5-10       | 20-50      | 中等并发，定期消息处理 |
| **大型应用**   | 10-20      | 50-100     | 高并发，频繁消息交互   |
| **微服务集群** | 3-8        | 15-30      | 每个服务实例的推荐配置 |

## 🚀 最佳实践

### 1. 消费者设计原则

```java
// ✅ 推荐：明确的异常分类处理
@Override
public Boolean handleMessage(final RabbitMQMessage message) {
    try {
        processMessage(message);
        return true;
    } catch (ValidationException e) {
        // 数据格式错误，不应重试
        log.error("消息格式错误，直接丢弃: {}", e.getMessage());
        return true;  // 避免无效重试
    } catch (ServiceUnavailableException e) {
        // 服务暂时不可用，应该重试
        log.warn("服务暂时不可用，等待重试: {}", e.getMessage());
        return false;  // 触发重试
    } catch (Exception e) {
        // 未知异常，谨慎处理
        log.error("未知异常", e);
        return shouldRetryUnknownError(e);
    }
}

// ❌ 避免：简单粗暴的异常处理
@Override
public Boolean handleMessage(final RabbitMQMessage message) {
    try {
        processMessage(message);
        return true;
    } catch (Exception e) {
        return false;  // 所有异常都重试，可能导致死循环
    }
}
```

### 2. 性能优化建议

```java
// ✅ 推荐：合理的prefetchCount设置
@RabbitConsumer(
    queueName = "high.throughput.queue",
    prefetchCount = 50,        // 高吞吐量场景
    autoAck = false           // 保证消息可靠性
)

@RabbitConsumer(
    queueName = "slow.processing.queue",
    prefetchCount = 1,         // 处理时间长的消息
    autoAck = false
)

// ✅ 推荐：批量处理优化
private final List<Message> messageBuffer = new ArrayList<>();

@Override
public Boolean handleMessage(final RabbitMQMessage message) {
    messageBuffer.add(parseMessage(message));

    if (messageBuffer.size() >= BATCH_SIZE) {
        return processBatch(messageBuffer);
    }

    return true;  // 单条消息也返回成功
}
```

### 3. 消费者生命周期管理

```java
// ✅ 推荐：明确的消费者启用/禁用配置
@RabbitConsumer(
    queueName = "production.queue",
    enabled = true,              // 生产环境启用
    autoReconnect = true
)
public class ProductionConsumer implements MessageConsumer { }

@RabbitConsumer(
    queueName = "test.queue",
    enabled = false,             // 禁用测试消费者
    autoReconnect = false        // 禁用的消费者无需自动重连
)
public class TestConsumer implements MessageConsumer { }

// ✅ 推荐：运行时查询消费者状态
public void checkConsumerStatus() {
    // 检查注册状态（是否被扫描到）
    boolean isRegistered = mqManager.isConsumerRegistered("production-consumer");

    // 检查活跃状态（是否实际运行）
    boolean isActive = mqManager.isConsumerActive("production-consumer");

    log.info("消费者状态 - 注册: {}, 活跃: {}", isRegistered, isActive);
}
```

### 4. 监控与运维

```java
// 健康检查端点
@GetMapping("/health/mq")
public JsonObject getMQHealth() {
    final JsonObject health = new JsonObject();

    // 消费者状态
    health.put("active_consumers", mqManager.getActiveConsumerCount());
    health.put("registered_consumers", mqManager.getRegisteredConsumerCount());

    // 连接池状态
    health.put("pool_stats", channelPool.getPoolStats());

    // 重试状态摘要
    health.put("retry_summary", mqManager.getConsumerRetryStatusSummary());

    return health;
}

// 监控指标收集
@Scheduled(fixedRate = 60000)  // 每分钟收集一次
public void collectMetrics() {
    final String stats = mqManager.getMonitor().getStatsString();
    metricsCollector.recordMQStats(stats);
}
```

## ⚠️ 故障排查指南

### 常见问题诊断

| 问题现象                 | 可能原因                 | 解决方案                                |
| ------------------------ | ------------------------ | --------------------------------------- |
| **消费者无法启动**       | 队列不存在               | 检查队列是否已创建，队列名是否正确      |
| **消息重复消费**         | 处理超时导致重新入队     | 增加处理超时时间，优化业务逻辑          |
| **消费者频繁重连**       | 网络不稳定或RabbitMQ重启 | 检查网络连接，增加重试间隔              |
| **消息发送失败**         | 连接池耗尽或交换机不存在 | 增加连接池大小，检查交换机配置          |
| **熔断器频繁开启**       | 下游服务异常             | 检查依赖服务状态，调整熔断阈值          |
| **健康检查误报连接断开** | 消费者被禁用但仍被检查   | 确认消费者配置`enabled=false`，重启应用 |

### 日志分析

```bash
# 查看消费者启动日志
grep "消费者.*启动成功" application.log

# 查看重连情况
grep "检测到消费者.*连接断开\|重连成功" application.log

# 查看熔断器状态
grep "熔断器.*开启\|熔断器.*重置" application.log

# 查看消息处理统计
grep "消费者统计" application.log | tail -1

# 查看健康检查详情
grep "执行消费者健康检查\|跳过健康检查" application.log

# 检查被禁用的消费者是否误触发重连
grep "未找到消费者.*的重试状态或熔断器" application.log
```

### 特殊问题排查

#### 消费者健康检查误报连接断开

**问题现象**：
```
WARN  com.vertx.template.mq.MQManager - 检测到消费者 order-status-consumer 连接断开，触发重连
WARN  c.v.t.m.c.ConsumerRetryManager - 未找到消费者 order-status-consumer 的重试状态或熔断器
```

**原因分析**：
- 消费者设置了 `enabled = false`，没有实际启动
- 健康检查仍然在检查这个被禁用的消费者
- 由于没有活跃连接，被误认为连接断开

**解决方案**：
1. **立即解决**：重启应用，修复后的代码会正确处理
2. **配置检查**：确认消费者配置一致性
   ```java
   @RabbitConsumer(
       queueName = "order.status.queue",
       enabled = false,           // 已禁用
       autoReconnect = false      // 建议同时禁用自动重连
   )
   ```
3. **日志确认**：检查应用重启后的日志
   ```bash
   # 应该看到类似日志
   grep "无活跃消费者，跳过健康检查\|跳过健康检查" application.log
   ```

## 📁 模块结构

```
mq/
├── MQManager.java                          # 核心管理器
├── config/                                 # 配置管理
│   ├── RabbitMqConfig.java                # RabbitMQ配置
│   └── ChannelPoolConfig.java             # 连接池配置
├── connection/                             # 连接管理
│   ├── ChannelPool.java                   # 连接池实现
│   └── RabbitMqConnectionManager.java     # 连接管理器
├── consumer/                               # 消费者模块
│   ├── RabbitConsumer.java                # 消费者注解
│   ├── MessageConsumer.java               # 消费者接口
│   ├── BasicConsumerMonitor.java          # 性能监控
│   └── ConsumerRetryManager.java          # 重试与熔断管理
└── enums/
    └── ExchangeType.java                   # 交换机类型枚举
```

## 🔮 升级路线

### 即将支持的特性

- **消息路由模式**：支持Topic、Direct、Fanout等多种路由模式
- **延迟队列**：内置延迟消息支持
- **死信处理**：自动死信队列配置
- **分布式事务**：基于RabbitMQ的分布式事务支持
- **消息压缩**：大消息自动压缩传输
- **动态配置**：运行时配置热更新

---

**📖 更多信息**

- [RabbitMQ 官方文档](https://www.rabbitmq.com/documentation.html)
- [Vert.x RabbitMQ Client](https://vertx.io/docs/vertx-rabbitmq-client/java/)
- [消息队列最佳实践](https://www.cloudamqp.com/blog/part1-rabbitmq-best-practice.html)
