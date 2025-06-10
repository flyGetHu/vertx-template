# RabbitMQ Prefetch Count 配置说明

## 📋 什么是Prefetch Count

Prefetch Count 是RabbitMQ中的QoS（Quality of Service）设置，用于控制消费者同时处理的**未确认消息数量**。它是实现**流量控制**和**负载均衡**的关键机制。

## 🏗️ 架构设计

本MQ系统采用**独立通道架构**：
- ✅ **每个消费者独立Channel**：每个 `@RabbitConsumer` 都有独立的RabbitMQClient和Channel
- ✅ **仅支持注解方式**：统一使用 `@RabbitConsumer` 声明式配置
- ✅ **简化配置**：无需考虑全局prefetch，每个消费者独立控制

### 架构优势
```
消费者A ─── 独立Channel ─── RabbitMQ Broker
消费者B ─── 独立Channel ─── RabbitMQ Broker
消费者C ─── 独立Channel ─── RabbitMQ Broker
```

- **隔离性**：消费者之间完全独立，互不影响
- **可靠性**：单个消费者故障不影响其他消费者
- **扩展性**：便于水平扩展和负载均衡

## 🔧 工作原理

### 基本概念
- **预取消息**：RabbitMQ会提前发送指定数量的消息给消费者
- **流量控制**：当未确认消息达到prefetch限制时，RabbitMQ停止发送新消息
- **背压机制**：防止快速生产者压垮慢速消费者

### 执行流程
```
1. 消费者设置 prefetchCount = 10
2. RabbitMQ 发送 10 条消息给消费者
3. 消费者开始处理消息（但还未ACK）
4. RabbitMQ 停止发送新消息
5. 消费者ACK一条消息后，RabbitMQ继续发送一条新消息
6. 维持未确认消息数量 ≤ 10
```

## ⚙️ 配置参数详解

### prefetchCount 参数

| 值范围   | 适用场景       | 优势               | 劣势             |
| -------- | -------------- | ------------------ | ---------------- |
| `0`      | 不推荐         | 无限制，最大吞吐量 | 可能导致内存溢出 |
| `1`      | 处理时间差异大 | 严格轮询，公平分发 | 吞吐量低         |
| `5-20`   | 一般业务场景   | 平衡性能和资源     | 需要根据业务调优 |
| `50-100` | 高吞吐量场景   | 高性能             | 内存消耗大       |
| `200+`   | 批处理场景     | 极高吞吐量         | 需要大量内存     |

## 🎯 最佳实践配置

### 生产环境推荐配置

```java
// 高频场景（如用户行为日志）
@RabbitConsumer(
    queueName = "user.behavior.queue",
    prefetchCount = 50,
    autoAck = false
)

// 普通业务场景（如订单处理）
@RabbitConsumer(
    queueName = "order.process.queue",
    prefetchCount = 20,
    autoAck = false
)

// 重型任务场景（如文件处理）
@RabbitConsumer(
    queueName = "file.process.queue",
    prefetchCount = 1,
    autoAck = false
)

// 批处理场景（如数据同步）
@RabbitConsumer(
    queueName = "data.sync.queue",
    prefetchCount = 100,
    autoAck = false
)
```

### 开发/测试环境配置

```java
@RabbitConsumer(
    queueName = "test.queue",
    prefetchCount = 5,          // 较小值便于调试
    autoAck = true             // 简化调试
)
```

## 📊 性能影响分析

### 1. 吞吐量 vs Prefetch Count

```
Prefetch = 1:    ████                    (基线 100%)
Prefetch = 10:   ████████                (200-300%)
Prefetch = 50:   ████████████            (300-400%)
Prefetch = 100:  ████████████████        (400-500%)
Prefetch = 0:    ██████████████████████  (500%+ 但不稳定)
```

### 2. 内存消耗 vs Prefetch Count

```
每条消息假设 1KB：
Prefetch = 10:   约 10KB/Consumer
Prefetch = 50:   约 50KB/Consumer
Prefetch = 100:  约 100KB/Consumer
Prefetch = 1000: 约 1MB/Consumer
```

## 🚨 常见问题及解决方案

### 问题1：消息堆积
**症状**：队列中消息数量持续增长
**原因**：prefetchCount设置过小，消费速度跟不上生产速度
**解决**：适当增加prefetchCount值

### 问题2：内存溢出
**症状**：消费者进程OOM
**原因**：prefetchCount设置过大，或设置为0
**解决**：降低prefetchCount值，增加消费者实例

### 问题3：负载不均衡
**症状**：某些消费者很忙，某些很闲
**原因**：消息处理时间差异大，prefetchCount设置不当
**解决**：设置prefetchCount=1，实现严格轮询

### 问题4：延迟增加
**症状**：消息处理延迟明显增加
**原因**：prefetchCount过大，消息在消费者内存中排队
**解决**：降低prefetchCount，优化消息处理逻辑

## 🔍 监控指标

### 关键监控项
1. **队列消息数量**：监控消息堆积情况
2. **消费者内存使用**：防止内存溢出
3. **消息处理延迟**：评估性能影响
4. **消费速率**：TPS/QPS监控
5. **未确认消息数量**：监控prefetch效果

### 监控告警阈值建议
```yaml
alerts:
  queue_messages: > 1000        # 队列消息堆积
  consumer_memory: > 80%        # 消费者内存使用率
  message_latency: > 30s        # 消息处理延迟
  unacked_messages: > prefetch*2 # 未确认消息数量
```

## 🎛️ 调优指南

### 第一步：基线测试
```java
@RabbitConsumer(
    queueName = "test.queue",
    prefetchCount = 20,    // 从默认值开始
    autoAck = false
)
```

### 第二步：压力测试
1. 监控关键指标（TPS、内存、延迟）
2. 逐步调整prefetchCount值
3. 记录不同值下的性能表现

### 第三步：优化选择
- **优先级：吞吐量** → 适当增加prefetchCount
- **优先级：低延迟** → 适当降低prefetchCount
- **优先级：稳定性** → 选择中等偏保守的值

### 第四步：生产验证
- 小流量灰度测试
- 监控关键指标
- 逐步扩大流量

## 📚 扩展阅读

- [RabbitMQ Consumer Prefetch](https://www.rabbitmq.com/consumer-prefetch.html)
- [RabbitMQ QoS Documentation](https://www.rabbitmq.com/confirms.html#channel-qos-prefetch)
- [Performance Tuning Guide](https://www.rabbitmq.com/performance-tuning.html)

---

**更新时间**: 2025-01-23
**架构特性**: 独立通道、注解驱动、简化配置
**配置示例**: 参见 `ExampleMessageConsumer.java`
