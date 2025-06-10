# MQ消费者ACK机制修复说明

## 🚨 问题描述

在原始代码中，MQ消费者存在严重的ACK机制缺失问题：

### 1. 配置矛盾
- `@RabbitConsumer` 注解的 `autoAck` 默认值为 `false`（手动确认模式）
- 但消息处理逻辑中完全没有手动ACK操作

### 2. 严重后果
- **内存泄漏**：未确认的消息会一直占用内存
- **重复消费**：连接断开后消息会重新投递，造成重复处理
- **队列堵塞**：可能导致消费者无法继续接收新消息

## ✅ 修复方案

### 1. 添加手动ACK逻辑

在 `handleMessage()` 方法中添加：

```java
// 消息处理成功时
if (result != null && result) {
    // 手动确认消息（仅在非自动确认模式下）
    if (!annotation.autoAck()) {
        try {
            final RabbitMQClient client = consumerClients.get(consumerName);
            if (client != null && client.isConnected()) {
                Future.await(client.basicAck(message.envelope().getDeliveryTag(), false));
            }
        } catch (Exception ackException) {
            log.error("确认消息失败", ackException);
        }
    }
}
```

### 2. 添加NACK逻辑

消息处理失败时，根据重试策略决定：

```java
// 消息处理失败时
if (currentRetries >= maxRetries) {
    // 重试次数已达上限，拒绝消息且不重新入队
    Future.await(client.basicNack(message.envelope().getDeliveryTag(), false, false));
} else {
    // 还可以重试，通过重新入队实现
    Future.await(client.basicNack(message.envelope().getDeliveryTag(), false, true));
}
```

### 3. 重试机制优化

对于手动确认模式，使用 `basicNack` 重新入队实现重试，而不是在内存中重复处理：

```java
// 手动确认模式：通过 nack 重新入队
Future.await(client.basicNack(message.envelope().getDeliveryTag(), false, true));

// 自动确认模式：延迟重新处理
vertx.setTimer(retryDelay, timerId -> {
    handleMessage(consumer, annotation, message);
});
```

## 📋 ACK策略总结

| 场景              | 操作                                   | 说明                   |
| ----------------- | -------------------------------------- | ---------------------- |
| 处理成功          | `basicAck(deliveryTag, false)`         | 确认消息，从队列中删除 |
| 处理失败 + 可重试 | `basicNack(deliveryTag, false, true)`  | 拒绝消息并重新入队     |
| 处理失败 + 不重试 | `basicNack(deliveryTag, false, false)` | 拒绝消息且不重新入队   |
| 自动确认模式      | 无需操作                               | 消息接收后自动确认     |

## 🔧 配置建议

### 生产环境推荐配置

```java
@RabbitConsumer(
    queueName = "your.queue",
    autoAck = false,        // 手动确认，保证消息可靠性
    maxRetries = 3,         // 适度重试
    retryDelayMs = 5000     // 5秒延迟重试
)
```

### 开发/测试环境配置

```java
@RabbitConsumer(
    queueName = "test.queue",
    autoAck = true,         // 自动确认，提高性能
    maxRetries = 1,         // 减少重试
    retryDelayMs = 1000     // 1秒延迟
)
```

## ⚠️ 注意事项

1. **确保队列存在**：根据内存记录，程序不负责创建队列，需要提前手动创建
2. **监控重试次数**：过多的重试可能影响性能
3. **错误日志**：注意监控ACK/NACK操作的错误日志
4. **连接状态**：ACK操作前检查客户端连接状态
5. **Prefetch配置**：合理设置prefetchCount，避免内存溢出或性能问题

## 🎯 最佳实践

1. **明确ACK策略**：根据业务需求选择自动或手动确认
2. **合理设置重试**：避免无限重试导致的资源浪费
3. **异常处理**：确保ACK/NACK操作的异常处理
4. **监控告警**：对重试次数和失败率进行监控
5. **Prefetch调优**：根据消息处理能力和内存情况调整预取数量

## 🆕 新增功能：Prefetch Count配置

### 配置说明
在 `@RabbitConsumer` 注解中新增了Prefetch Count参数：

```java
@RabbitConsumer(
    queueName = "your.queue",
    autoAck = false,
    prefetchCount = 20        // 预取消息数量（新增）
)
```

### 参数详解
- **`prefetchCount`**：控制消费者同时处理的未确认消息数量（默认20）
- **独立通道架构**：每个消费者使用独立Channel，无需globalPrefetch配置

### 推荐配置
| 场景       | prefetchCount | 说明                       |
| ---------- | ------------- | -------------------------- |
| 轻量级任务 | 50-100        | 高吞吐量，适合简单处理逻辑 |
| 一般业务   | 10-50         | 平衡性能和资源消耗         |
| 重型任务   | 1-10          | 避免消息堆积，适合复杂处理 |
| 批处理     | 100+          | 最大化吞吐量，需要充足内存 |

### 架构优势
```
消费者A ─── 独立Channel ─── RabbitMQ
消费者B ─── 独立Channel ─── RabbitMQ
消费者C ─── 独立Channel ─── RabbitMQ
```

- **隔离性**：消费者之间完全独立
- **可靠性**：单点故障不影响其他消费者
- **简化配置**：无需考虑全局prefetch设置

---

**修复时间**: 2025-01-23
**影响范围**: 所有使用 `@RabbitConsumer` 的消费者
**向后兼容**: 完全兼容，现有代码无需修改
