# MqVerticle 使用指南

## 📋 概述

`MqVerticle` 是Vert.x模板项目中的消息队列管理模块，基于RabbitMQ实现，负责：

- ✅ MQ配置验证和加载
- ✅ RabbitMQ连接管理器初始化
- ✅ 自动扫描并启动消费者
- ✅ MQ服务生命周期管理
- ✅ 消费者监控和统计

## 🚀 快速开始

### 1. 配置 RabbitMQ

在 `src/main/resources/config.yml` 中配置RabbitMQ连接信息：

```yaml
# 消息队列配置
mq:
  rabbitmq:
    enabled: true # 是否启用RabbitMQ
    host: 127.0.0.1
    port: 5672
    user: guest
    password: guest
    virtualHost: /
    useSsl: false
    connectionTimeout: 60000
    requestedHeartbeat: 60
    handshakeTimeout: 10000
    requestedChannelMax: 5
    networkRecoveryInterval: 5000
    automaticRecovery: true
```

### 2. 创建消费者

实现 `MessageConsumer` 接口并使用 `@RabbitConsumer` 注解：

```java
@Slf4j
@Singleton
@RabbitConsumer(
    queueName = "user.events",
    enabled = true,
    autoAck = false,
    maxRetries = 3,
    retryDelayMs = 1000
)
public class UserEventConsumer implements MessageConsumer {

    @Override
    public String getConsumerName() {
        return "UserEventConsumer";
    }

    @Override
    public Boolean handleMessage(RabbitMQMessage message) {
        String body = message.body().toString();
        log.info("处理用户事件: {}", body);

        try {
            // 业务逻辑处理
            processUserEvent(body);
            return true; // 处理成功
        } catch (Exception e) {
            log.error("处理失败", e);
            return false; // 处理失败，触发重试
        }
    }

    @Override
    public void onStart() {
        log.info("用户事件消费者启动");
    }

    private void processUserEvent(String eventData) {
        // 实现具体的业务逻辑
    }
}
```

### 3. 启动应用

`MqVerticle` 会在应用启动时自动：

1. 验证MQ配置
2. 初始化连接管理器
3. 扫描并启动所有消费者

## 📝 @RabbitConsumer 注解参数

| 参数           | 类型    | 默认值 | 说明                 |
| -------------- | ------- | ------ | -------------------- |
| `queueName`    | String  | 必填   | 队列名称             |
| `enabled`      | boolean | true   | 是否启用此消费者     |
| `autoAck`      | boolean | false  | 是否自动确认消息     |
| `maxRetries`   | int     | 3      | 最大重试次数         |
| `retryDelayMs` | long    | 1000   | 重试延迟时间（毫秒） |

## 🏗️ 架构特点

### 依赖注入集成
- 使用 Google Guice 管理依赖
- 消费者自动注册到IoC容器
- 支持依赖注入到消费者中

### 自动发现机制
- 自动扫描 `com.vertx.template` 包
- 发现带 `@RabbitConsumer` 注解的类
- 自动实例化并启动消费者

### 监控与统计
- 内置消费者监控器
- 统计成功/失败/重试次数
- 记录平均处理时间
- 提供监控数据查询API

## 📊 监控功能

### 获取监控数据

```java
@Inject
private MQManager mqManager;

// 获取所有消费者统计
JsonObject stats = mqManager.getMonitor().getAllStats();

// 获取活跃消费者数量
int activeCount = mqManager.getActiveConsumerCount();

// 检查特定消费者状态
boolean isActive = mqManager.isConsumerActive("UserEventConsumer");
```

### 运行时管理

```java
// 停止特定消费者
mqManager.stopConsumer("UserEventConsumer");

// 重新启动消费者
mqManager.startConsumer("UserEventConsumer");

// 停止所有消费者
mqManager.stopAllConsumers();
```

## 🔧 最佳实践

### 1. 队列预创建
根据记忆，MQ系统不负责创建队列或交换机，需要手动预创建：

```bash
# 使用RabbitMQ管理界面或命令行工具创建
rabbitmqctl declare queue name=user.events durable=true
```

### 2. 错误处理
```java
@Override
public Boolean handleMessage(RabbitMQMessage message) {
    try {
        // 业务处理逻辑
        processMessage(message.body().toString());
        return true;
    } catch (BusinessException e) {
        // 业务异常，不重试
        log.error("业务处理失败，不重试: {}", e.getMessage());
        return true; // 返回true避免重试
    } catch (Exception e) {
        // 系统异常，允许重试
        log.error("系统异常，允许重试", e);
        return false;
    }
}
```

### 3. 消息幂等性
```java
private void processUserEvent(String eventData) {
    JsonObject event = new JsonObject(eventData);
    String eventId = event.getString("eventId");

    // 检查是否已处理过
    if (isEventProcessed(eventId)) {
        log.info("事件已处理，跳过: {}", eventId);
        return;
    }

    // 处理事件
    handleEvent(event);

    // 标记为已处理
    markEventAsProcessed(eventId);
}
```

### 4. 优雅关闭
MqVerticle自动处理优雅关闭：
- 停止所有消费者
- 关闭连接管理器
- 清理资源

## 🐛 故障排查

### 常见问题

1. **队列不存在**
   ```
   启动消费者失败 - 请确保队列 user.events 已存在
   ```
   **解决**：在RabbitMQ中手动创建队列

2. **连接失败**
   ```
   RabbitMQ连接管理器初始化失败
   ```
   **解决**：检查配置文件中的连接参数

3. **没有找到消费者**
   ```
   未找到任何消费者
   ```
   **解决**：确保消费者类在正确的包下并标注了注解

### 日志级别配置

```yaml
logging:
  level:
    com.vertx.template.mq: DEBUG
    io.vertx.rabbitmq: INFO
```

## 📈 性能调优

### 连接池配置
```yaml
mq:
  rabbitmq:
    requestedChannelMax: 10 # 增加通道数
    networkRecoveryInterval: 3000 # 减少恢复间隔
```

### 消费者配置
```java
@RabbitConsumer(
    queueName = "high-throughput.queue",
    autoAck = true, // 高吞吐量场景使用自动确认
    maxRetries = 1  // 减少重试次数
)
```

## 🔗 相关文档

- [RabbitMQ官方文档](https://www.rabbitmq.com/documentation.html)
- [Vert.x RabbitMQ Client](https://vertx.io/docs/vertx-rabbitmq-client/java/)
- [Google Guice用户指南](https://github.com/google/guice/wiki/GettingStarted)
