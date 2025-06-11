# RabbitMQ 模块使用指南

## 📋 模块概述

简化的 RabbitMQ 消息队列模块，提供消息生产和消费功能。

**核心特性：**
- 自动消费者扫描和注册
- 连接池管理（用于发送消息）
- 自动重连机制
- 基本监控统计

## 🚀 快速开始

### 1. 配置

```yaml
# config.yml
mq:
  rabbitmq:
    enabled: true
    host: localhost
    port: 5672
    user: guest
    password: guest

    # 连接池配置
    pool:
      initial_size: 5
      max_size: 20
```

### 2. 创建消费者

```java
@RabbitConsumer(
    queueName = "user.created",
    enabled = true,
    autoAck = false,
    maxRetries = 3,
    prefetchCount = 10
)
@Singleton
public class UserCreatedConsumer implements MessageConsumer {

    @Override
    public String getConsumerName() {
        return "user-created-consumer";
    }

    @Override
    public Boolean handleMessage(RabbitMQMessage message) {
        try {
            String payload = message.body().toString();
            log.info("处理用户创建消息: {}", payload);

            // 处理业务逻辑
            processUserCreated(payload);

            return true; // 处理成功
        } catch (Exception e) {
            log.error("处理消息失败", e);
            return false; // 处理失败，会重试
        }
    }
}
```

### 3. 发送消息

```java
@Inject
private MQManager mqManager;

// 发送到队列
mqManager.sendToQueue("user.created", "用户创建消息");

// 发送JSON到队列
JsonObject userData = new JsonObject()
    .put("userId", "123")
    .put("action", "created");
mqManager.sendJsonToQueue("user.created", userData);

// 发送到交换机
mqManager.sendToExchange("user.exchange", "created", "消息内容");
```

### 4. 启动消费者

```java
// 在 MainVerticle 中
@Inject
private MQManager mqManager;

// 自动扫描并启动所有消费者
mqManager.scanAndStartConsumers("com.vertx.template.consumer");
```

## 📊 监控功能

```java
// 获取消费者统计
String stats = mqManager.getMonitor().getStatsString();
log.info(stats);

// 获取连接池状态
String poolStats = channelPool.getPoolStats();
log.info(poolStats);
```

## ⚠️ 重要说明

1. **队列和交换机需要预先创建** - 程序不会自动创建基础设施
2. **消费者独立连接** - 每个消费者使用独立的连接，故障隔离
3. **发送者共享连接池** - 提高发送消息的性能
4. **自动重连** - 连接断开时会自动重连

## 🔧 故障排查

- 检查 RabbitMQ 服务是否运行
- 确认队列和交换机已正确创建
- 查看应用日志中的连接和消息处理信息
- 使用监控接口检查消费者状态

## 📁 模块结构

```
mq/
├── MQManager.java              # 主要管理器
├── connection/                 # 连接管理
│   ├── ChannelPool.java       # 连接池
│   └── RabbitMqConnectionManager.java
├── config/                     # 配置
│   ├── RabbitMqConfig.java
│   └── ChannelPoolConfig.java
├── consumer/                   # 消费者相关
│   ├── RabbitConsumer.java    # 消费者注解
│   ├── MessageConsumer.java   # 消费者接口
│   └── BasicConsumerMonitor.java
└── enums/
    └── ExchangeType.java
```
