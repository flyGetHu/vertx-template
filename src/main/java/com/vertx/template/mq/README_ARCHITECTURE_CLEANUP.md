# MQ架构优化：独立通道 + 纯注解驱动

## 🎯 优化目标

基于以下设计决策进行架构优化：
1. **仅支持注解方式** - 统一使用 `@RabbitConsumer` 声明式配置
2. **每个消费者独立通道** - 提供更好的隔离性和可靠性

## 🏗️ 架构特点

### 独立通道架构
```
应用进程
├── 消费者A ─── 独立RabbitMQClient ─── 独立Channel ─── RabbitMQ Broker
├── 消费者B ─── 独立RabbitMQClient ─── 独立Channel ─── RabbitMQ Broker
└── 消费者C ─── 独立RabbitMQClient ─── 独立Channel ─── RabbitMQ Broker
```

### 优势分析

| 特性       | 传统共享Channel          | 独立Channel架构              |
| ---------- | ------------------------ | ---------------------------- |
| **隔离性** | ❌ 消费者间相互影响       | ✅ 完全独立，互不影响         |
| **可靠性** | ❌ 单点故障影响所有消费者 | ✅ 单个消费者故障不影响其他   |
| **扩展性** | ❌ 需要考虑全局配置       | ✅ 每个消费者独立配置         |
| **调试性** | ❌ 难以定位具体消费者问题 | ✅ 问题定位精确到具体消费者   |
| **性能**   | ✅ 资源共享               | ⚖️ 略多资源消耗，但隔离性更好 |

## 🚀 本次优化内容

### 1. 移除globalPrefetch配置
**原因**：每个消费者使用独立Channel，globalPrefetch概念不再适用

**变更对比**：
```java
// 🔴 优化前
@RabbitConsumer(
    queueName = "test.queue",
    prefetchCount = 20,
    globalPrefetch = false  // 冗余配置
)

// ✅ 优化后
@RabbitConsumer(
    queueName = "test.queue",
    prefetchCount = 20      // 自动应用到独立Channel
)
```

### 2. 简化QoS设置逻辑
**优化前**：
```java
Future.await(client.basicQos(prefetchCount, globalPrefetch));
```

**优化后**：
```java
Future.await(client.basicQos(prefetchCount));
```

### 3. 更新文档和示例
- 移除所有globalPrefetch相关说明
- 强调独立通道架构的优势
- 简化配置示例

## 📋 消费者开发指南

### 标准消费者模板
```java
@Slf4j
@Singleton
@RabbitConsumer(
    queueName = "your.business.queue",
    enabled = true,
    autoAck = false,              // 推荐手动确认
    maxRetries = 3,
    retryDelayMs = 1000,
    prefetchCount = 20,           // 根据业务调整
    description = "业务描述"
)
public class YourBusinessConsumer implements MessageConsumer {

    @Override
    public String getConsumerName() {
        return "your-business-consumer";
    }

    @Override
    public Boolean handleMessage(RabbitMQMessage message) {
        try {
            // 业务逻辑处理
            log.info("处理消息: {}", message.body().toString());

            // 返回true表示处理成功
            return true;

        } catch (Exception e) {
            log.error("消息处理失败", e);

            // 返回false表示处理失败，会触发重试
            return false;
        }
    }

    @Override
    public void onStart() {
        log.info("消费者启动：{}", getConsumerName());
    }

    @Override
    public void onStop() {
        log.info("消费者停止：{}", getConsumerName());
    }
}
```

### 配置最佳实践

| 业务场景                   | prefetchCount | autoAck | maxRetries | 说明             |
| -------------------------- | ------------- | ------- | ---------- | ---------------- |
| **高频轻量级**（如日志）   | 50-100        | false   | 1          | 追求高吞吐量     |
| **普通业务**（如订单）     | 10-30         | false   | 3          | 平衡性能和可靠性 |
| **重型任务**（如文件处理） | 1-5           | false   | 5          | 避免资源过载     |
| **批处理**（如数据同步）   | 100+          | false   | 0          | 最大化吞吐量     |
| **测试调试**               | 1-5           | true    | 0          | 便于调试         |

## 🎛️ 系统启动流程

### 1. 自动扫描启动
```java
// MqVerticle.java 中的启动流程
mqManager.scanAndStartConsumers("com.vertx.template");
```

### 2. 消费者创建流程
```
1. 扫描 @RabbitConsumer 注解类
2. 通过 Guice 创建消费者实例
3. 为每个消费者创建独立 RabbitMQClient
4. 设置独立的 QoS (prefetchCount)
5. 创建 RabbitMQConsumer 并绑定队列
6. 启动消息监听
```

### 3. 生命周期管理
```java
// 启动单个消费者
mqManager.startConsumer("consumer-name");

// 停止单个消费者
mqManager.stopConsumer("consumer-name");

// 停止所有消费者
mqManager.stopAllConsumers();
```

## 🔍 监控和调试

### 消费者状态查询
```java
// 获取活跃消费者数量
int activeCount = mqManager.getActiveConsumerCount();

// 获取注册消费者数量
int registeredCount = mqManager.getRegisteredConsumerCount();

// 检查特定消费者状态
boolean isRunning = mqManager.isConsumerActive("consumer-name");
```

### 关键监控指标
1. **独立Channel数量** = 活跃消费者数量
2. **每个消费者的QoS设置** = prefetchCount值
3. **消费者级别的错误率**
4. **消费者级别的处理延迟**
5. **未确认消息数量**（按消费者分别统计）

## ⚠️ 注意事项

### 1. 资源管理
- **优势**：故障隔离，问题定位精确
- **成本**：每个消费者需要独立TCP连接
- **建议**：合理规划消费者数量，避免过多连接

### 2. 配置管理
- **简化**：无需考虑global配置
- **独立**：每个消费者可以独立调优
- **建议**：建立配置模板和最佳实践

### 3. 错误处理
- **隔离性**：单个消费者错误不影响其他
- **监控**：需要分消费者监控错误率
- **建议**：建立消费者级别的告警机制

## 📚 相关文档

- [Prefetch Count 配置详解](README_PREFETCH_CONFIG.md)
- [ACK机制修复说明](README_ACK_FIX.md)
- [消费者开发示例](example/ExampleMessageConsumer.java)

---

**更新时间**: 2025-01-23
**架构版本**: v2.0 - 独立通道 + 纯注解驱动
**主要变更**: 移除globalPrefetch，简化配置，强化隔离性
