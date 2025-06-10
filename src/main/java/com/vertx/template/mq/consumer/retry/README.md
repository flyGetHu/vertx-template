# Vert.x RabbitMQ 消费者重试系统

本模块提供了完善的 RabbitMQ 消费者重试机制，包含指数退避重试、死信队列处理和消息追踪功能。

## 核心组件

### 1. RetryableMessage
可重试消息包装类，用于携带重试相关的元数据。

**主要功能：**
- 包装原始 RabbitMQMessage
- 记录重试次数和失败历史
- 自动提取和管理消息头信息
- 支持扩展属性

### 2. RetryHandler
消息重试处理器，实现指数退避重试机制。

**主要功能：**
- 可配置的重试策略（最大重试次数、延迟时间、倍数等）
- 指数退避算法
- 与死信队列集成
- 重试统计信息

### 3. DeadLetterHandler
死信队列处理器，管理死信消息。

**主要功能：**
- 自动创建死信队列
- 可配置的死信处理回调
- 死信统计信息
- 死信消息元数据记录

### 4. RetryMessagePublisher
重试消息发布器，负责重新发送消息。

**主要功能：**
- 重试消息发布
- 死信消息发布
- 延迟消息发布
- 消息属性管理

### 5. EnhancedConsumerManager
增强版消费者管理器，集成重试机制。

**主要功能：**
- 自动集成重试逻辑
- 消费者配置管理
- 失败消息自动处理

## 使用示例

### 基础重试配置

```java
@Inject
private RetryHandler retryHandler;

public void configureBasicRetry() {
    String consumerName = "order-consumer";

    // 创建重试策略
    RetryHandler.RetryStrategy strategy = RetryHandler.RetryStrategy.builder()
        .maxRetries(3)          // 最大重试3次
        .initialDelay(1000)     // 初始延迟1秒
        .maxDelay(30000)        // 最大延迟30秒
        .multiplier(2.0)        // 指数倍数2.0
        .build();

    retryHandler.setRetryStrategy(consumerName, strategy);
}
```

### 死信队列配置

```java
public void configureDeadLetter() {
    String consumerName = "payment-consumer";
    String deadLetterQueue = "payment.dlq";

    // 创建死信队列配置
    DeadLetterHandler.DeadLetterConfig config =
        new DeadLetterHandler.DeadLetterConfig(deadLetterQueue, this::handleDeadLetter);

    retryHandler.configureDeadLetter(consumerName, config);
}

private Future<Void> handleDeadLetter(RetryableMessage message, Throwable cause) {
    // 自定义死信处理逻辑
    log.error("处理死信消息: {}", cause.getMessage());
    return Future.succeededFuture();
}
```

### 消费者实现

```java
@Singleton
public class OrderConsumer implements MessageConsumer {

    @Inject
    private RetryHandler retryHandler;

    @Override
    public String getConsumerName() {
        return "order-consumer";
    }

    @Override
    public Future<Boolean> handleMessage(RabbitMQMessage message) {
        try {
            // 处理订单消息
            processOrder(message.body().toString());
            return Future.succeededFuture(true);
        } catch (Exception e) {
            return Future.failedFuture(e);
        }
    }

    @Override
    public Future<Void> onMessageFailed(RabbitMQMessage message, Throwable cause) {
        // 自动触发重试机制
        String queueName = getConsumerName() + ".queue";
        return retryHandler.handleRetry(getConsumerName(), message, cause, queueName)
            .map(result -> null);
    }
}
```

## 重试策略配置

### 指数退避算法

重试延迟按以下公式计算：
```
delay = min(initialDelay * multiplier^retryCount, maxDelay)
```

**示例配置：**
- 第1次重试：1秒后
- 第2次重试：2秒后
- 第3次重试：4秒后
- 第4次重试：8秒后
- 第5次重试：16秒后（如果maxDelay允许）

### 常用配置模板

#### 快速重试（适用于临时性错误）
```java
RetryHandler.RetryStrategy.builder()
    .maxRetries(3)
    .initialDelay(500)
    .maxDelay(5000)
    .multiplier(1.5)
    .build();
```

#### 标准重试（适用于一般业务）
```java
RetryHandler.RetryStrategy.builder()
    .maxRetries(5)
    .initialDelay(1000)
    .maxDelay(30000)
    .multiplier(2.0)
    .build();
```

#### 谨慎重试（适用于关键业务）
```java
RetryHandler.RetryStrategy.builder()
    .maxRetries(10)
    .initialDelay(2000)
    .maxDelay(300000)  // 5分钟
    .multiplier(1.8)
    .build();
```

## 死信队列处理

### 默认死信队列

如果没有配置死信队列，系统会自动创建默认死信队列：
```
{consumerName}.dlq
```

### 自定义死信处理

```java
DeadLetterHandler.DeadLetterConfig config =
    new DeadLetterHandler.DeadLetterConfig("custom.dlq", (message, cause) -> {
        // 发送告警
        alertService.sendAlert("Dead letter detected", cause);

        // 记录到数据库
        deadLetterRepository.save(new DeadLetterRecord(message, cause));

        // 发送到监控系统
        monitoringService.recordDeadLetter(message.getConsumerName());

        return Future.succeededFuture();
    });
```

## 监控和统计

### 重试统计

```java
RetryHandler.RetryStats stats = retryHandler.getRetryStats("order-consumer");
System.out.println("总重试次数: " + stats.getTotalRetries());
System.out.println("成功重试次数: " + stats.getSuccessfulRetries());
System.out.println("最后重试时间: " + stats.getLastRetryTime());
```

### 死信统计

```java
DeadLetterHandler.DeadLetterStats dlqStats = deadLetterHandler.getDeadLetterStats("order-consumer");
System.out.println("死信总数: " + dlqStats.getTotalDeadLetters());
System.out.println("最后死信时间: " + dlqStats.getLastDeadLetterTime());
```

## 最佳实践

### 1. 重试策略选择

- **瞬时错误**：网络抖动、临时服务不可用 → 快速重试
- **业务错误**：数据格式错误、业务规则冲突 → 直接进入死信队列
- **资源错误**：数据库连接、外部服务超时 → 标准重试
- **关键业务**：支付、订单处理 → 谨慎重试

### 2. 消息幂等性

确保消息处理是幂等的，避免重试导致的重复处理：

```java
@Override
public Future<Boolean> handleMessage(RabbitMQMessage message) {
    String messageId = message.properties().getMessageId();

    // 检查是否已处理
    if (isAlreadyProcessed(messageId)) {
        return Future.succeededFuture(true);
    }

    // 处理消息
    return processMessage(message)
        .onSuccess(result -> markAsProcessed(messageId));
}
```

### 3. 错误分类

根据错误类型决定是否重试：

```java
@Override
public Future<Void> onMessageFailed(RabbitMQMessage message, Throwable cause) {
    if (isRetryableError(cause)) {
        // 可重试错误：网络异常、超时等
        return retryHandler.handleRetry(getConsumerName(), message, cause, getQueueName())
            .map(result -> null);
    } else {
        // 不可重试错误：数据格式错误、业务规则冲突等
        return retryHandler.handleDeadLetter(getConsumerName(), message, cause)
            .map(result -> null);
    }
}

private boolean isRetryableError(Throwable cause) {
    return cause instanceof TimeoutException ||
           cause instanceof ConnectException ||
           cause instanceof SocketException;
}
```

### 4. 资源管理

及时关闭不需要的资源：

```java
@PreDestroy
public void cleanup() {
    retryMessagePublisher.closeAllPublishers()
        .onComplete(ar -> log.info("重试发布器已关闭"));
}
```

## 配置参数

### 重试策略参数

| 参数         | 说明             | 默认值 | 建议范围    |
| ------------ | ---------------- | ------ | ----------- |
| maxRetries   | 最大重试次数     | 3      | 1-10        |
| initialDelay | 初始延迟（毫秒） | 1000   | 100-5000    |
| maxDelay     | 最大延迟（毫秒） | 30000  | 5000-600000 |
| multiplier   | 延迟倍数         | 2.0    | 1.1-3.0     |

### 性能调优

- **高并发场景**：减少重试次数，增加初始延迟
- **低延迟要求**：减少初始延迟，降低倍数
- **资源受限**：限制最大延迟，避免资源占用过久

## 注意事项

1. **消息顺序**：重试机制可能会影响消息处理顺序
2. **资源消耗**：大量重试会增加系统负载
3. **死循环**：确保最终会进入死信队列，避免无限重试
4. **监控告警**：及时发现和处理死信消息
5. **数据一致性**：注意重试过程中的数据一致性问题

## 扩展功能

### 条件重试

```java
// 根据错误类型决定重试策略
RetryHandler.RetryStrategy getRetryStrategy(Throwable cause) {
    if (cause instanceof TimeoutException) {
        return quickRetryStrategy;
    } else if (cause instanceof SQLException) {
        return standardRetryStrategy;
    } else {
        return conservativeRetryStrategy;
    }
}
```

### 动态配置

```java
// 根据系统负载动态调整重试策略
public void adjustRetryStrategy(String consumerName) {
    double systemLoad = getSystemLoad();
    RetryHandler.RetryStrategy strategy = calculateOptimalStrategy(systemLoad);
    retryHandler.setRetryStrategy(consumerName, strategy);
}
```
