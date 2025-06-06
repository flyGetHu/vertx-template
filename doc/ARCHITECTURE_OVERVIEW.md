# Vert.x Template 项目架构总览

本文档提供 Vert.x Template 项目的架构总览，包含核心模块设计、重构成果和相关文档索引。

## 📋 目录

- [项目概述](#项目概述)
- [核心模块](#核心模块)
- [重构成果](#重构成果)
- [架构文档索引](#架构文档索引)
- [开发规范](#开发规范)
- [相关文档](#相关文档)

## 🎯 项目概述

Vert.x Template 是一个基于 Vert.x 框架的企业级应用模板，提供完整的 Web 服务开发基础设施，包括路由管理、认证授权、限流控制、参数验证等核心功能。

### 技术栈

- **核心框架**: Vert.x 4.x
- **依赖注入**: Google Guice
- **JSON处理**: Jackson
- **参数验证**: Jakarta Validation
- **日志框架**: SLF4J + Logback

## 🏗️ 核心模块

### 路由器模块 (Router Module)

**📂 位置**: `src/main/java/com/vertx/template/router/`
**📚 详细文档**: [路由器架构设计](modules/router-architecture.md)

#### 重构前后对比

| 方面         | 重构前         | 重构后       |
| ------------ | -------------- | ------------ |
| **文件结构** | 单体文件 813行 | 4个专职组件  |
| **职责分离** | 混合多种职责   | 单一职责原则 |
| **可测试性** | 困难           | 组件化测试   |
| **可维护性** | 复杂耦合       | 清晰解耦     |

#### 组件架构

```
AnnotationRouterHandler (协调器)
├── RouteScanner (扫描器)          # 控制器扫描
├── ParameterResolver (解析器)      # 参数解析
└── RequestExecutor (执行器)        # 方法执行
```

### 中间件模块 (Middleware Module)

**📂 位置**: `src/main/java/com/vertx/template/middleware/`
**📚 详细文档**: [middleware-integration.md](middleware-integration.md)

#### 核心中间件

- **认证中间件**: JWT、Session认证支持
- **限流中间件**: 基于令牌桶算法的限流控制
- **响应中间件**: 统一响应格式处理
- **验证中间件**: 请求参数验证

### 异常处理模块 (Exception Module)

**📂 位置**: `src/main/java/com/vertx/template/exception/`

#### 异常层次结构

```
Exception
└── BusinessException (业务异常基类)
    ├── AuthenticationException (认证异常)
    ├── ValidationException (验证异常)
    ├── RateLimitException (限流异常)
    └── RouteRegistrationException (路由注册异常)
```

## 🚀 重构成果

### 📊 量化指标

| 指标             | 重构前         | 重构后         | 改进幅度 |
| ---------------- | -------------- | -------------- | -------- |
| **最大文件行数** | 813行          | 318行          | ↓ 61%    |
| **平均文件行数** | 813行          | 180行          | ↓ 78%    |
| **组件职责数**   | 7个/文件       | 1个/文件       | ↓ 86%    |
| **测试复杂度**   | 高（大量Mock） | 低（独立测试） | ↓ 70%    |

### ✅ 规范符合性

- ✅ **文件层级**: 不超过3级
- ✅ **代码量限制**: 每个文件≤800行
- ✅ **职责单一**: 每个组件职责明确
- ✅ **命名规范**: 大驼峰类名，小驼峰方法名
- ✅ **注释要求**: 完整的功能和参数描述

### 🎯 设计原则遵循

1. **单一职责原则 (SRP)**: 每个组件只负责一个明确职责
2. **开闭原则 (OCP)**: 支持扩展，无需修改现有代码
3. **依赖倒置原则 (DIP)**: 依赖抽象而非具体实现
4. **接口隔离原则 (ISP)**: 提供专门的方法接口

## 📚 架构文档索引

### 核心架构文档

| 文档                                             | 描述                   | 最后更新 |
| ------------------------------------------------ | ---------------------- | -------- |
| [路由器架构设计](modules/router-architecture.md) | 路由器模块重构详细设计 | 🆕 新增   |
| [路由模块设计指南](router-module.md)             | 路由模块设计和优化策略 | 🔄 已更新 |
| [中间件集成](middleware-integration.md)          | 中间件架构和集成方案   | ✅ 稳定   |

### 功能模块文档

| 文档                                | 描述              | 状态   |
| ----------------------------------- | ----------------- | ------ |
| [依赖注入](dependency-injection.md) | Guice依赖注入配置 | ✅ 稳定 |
| [安全模块](SECURITY_README.md)      | 认证和授权机制    | ✅ 稳定 |
| [限流模块](RATELIMIT_README.md)     | 限流策略和配置    | ✅ 稳定 |
| [注解使用指南](ANNOTATION_USAGE.md) | 项目注解使用规范  | ✅ 稳定 |

### 技术文档

| 文档                                                    | 描述           | 状态   |
| ------------------------------------------------------- | -------------- | ------ |
| [中间件重构总结](MIDDLEWARE_REFACTOR_SUMMARY.md)        | 中间件重构历程 | 📝 历史 |
| [依赖注入修复总结](DEPENDENCY_INJECTION_FIX_SUMMARY.md) | DI问题修复记录 | 📝 历史 |
| [ID生成策略](ID_GENERATION_STRATEGY.md)                 | 唯一ID生成方案 | ✅ 稳定 |

## 🛠️ 开发规范

### 代码结构规范

项目采用标准的分层架构设计，详细的目录结构请参考 [项目结构文档](PROJECT_STRUCTURE.md)。

#### 核心模块概览

```
src/main/java/com/vertx/template/
├── Run.java                    # 应用程序入口
├── config/                     # 配置模块
├── controller/                 # 控制器层 (Web层)
├── service/                    # 服务层 (业务逻辑层)
│   └── impl/                   # 服务实现
├── repository/                 # 数据访问层 (DAO层)
│   ├── common/                 # 通用仓储
│   └── impl/                   # 仓储实现
├── model/                      # 数据模型层
│   ├── entity/                 # 数据库实体
│   ├── dto/                    # 数据传输对象
│   └── context/                # 上下文对象
├── router/                     # 路由系统
│   ├── handler/                # 路由处理器
│   ├── scanner/                # 路由扫描器
│   ├── resolver/               # 参数解析器
│   ├── executor/               # 请求执行器
│   └── cache/                  # 路由缓存
├── middleware/                 # 中间件模块
│   ├── auth/                   # 认证中间件
│   ├── ratelimit/              # 限流中间件
│   ├── response/               # 响应中间件
│   └── validation/             # 验证中间件
├── di/                         # 依赖注入模块
├── exception/                  # 异常定义
├── utils/                      # 工具类
└── verticle/                   # Verticle模块
```

### 命名规范

- **类名**: 大驼峰 (UserService)
- **方法名**: 小驼峰 (getUserInfo)
- **常量**: 全大写+下划线 (MAX_RETRY_TIMES)
- **包名**: 小写+点分隔 (com.vertx.template.router)

### 注释规范

```java
/**
 * 参数解析器，专门负责解析HTTP请求中的各种参数
 *
 * @功能描述 解析路径参数、查询参数、请求体、请求头等
 * @职责范围 参数提取、类型转换、参数验证
 */
public class ParameterResolver {

    /**
     * 解析方法参数
     *
     * @param metadata 方法元数据（可能为null）
     * @param method 方法信息
     * @param ctx 路由上下文
     * @return 解析后的参数数组
     */
    public Object[] resolveArguments(MethodMetadata metadata, Method method, RoutingContext ctx) {
        // 实现逻辑
    }
}
```

## 🔮 未来规划

### 短期目标 (1-2个月)

- [ ] 完善单元测试覆盖率至80%+
- [ ] 添加性能监控指标
- [ ] 优化参数解析性能

### 中期目标 (3-6个月)

- [ ] 支持异步参数解析
- [ ] 实现请求追踪功能
- [ ] 添加API文档自动生成

### 长期目标 (6个月+)

- [ ] 支持微服务部署
- [ ] 集成分布式链路追踪
- [ ] 实现自动化运维工具

## 📖 相关文档

### 详细文档索引

| 文档类型       | 文档名称                                        | 描述                           |
| -------------- | ----------------------------------------------- | ------------------------------ |
| **项目结构**   | [项目结构文档](PROJECT_STRUCTURE.md)           | 详细的代码结构和组织方式       |
| **核心架构**   | [路由器架构设计](modules/router-architecture.md) | 路由器模块重构详细设计         |
| **功能模块**   | [依赖注入指南](dependency-injection.md)        | Guice依赖注入配置和使用        |
| **安全认证**   | [安全模块文档](SECURITY_README.md)             | 认证和授权机制                 |
| **限流控制**   | [限流模块文档](RATELIMIT_README.md)            | 限流策略和配置                 |
| **注解使用**   | [注解使用指南](ANNOTATION_USAGE.md)            | 项目注解使用规范               |
| **中间件**     | [中间件集成文档](middleware-integration.md)    | 中间件架构和集成方案           |

### 开发指南

- **新手入门**: 建议先阅读 [项目结构文档](PROJECT_STRUCTURE.md) 了解整体架构
- **功能开发**: 参考 [注解使用指南](ANNOTATION_USAGE.md) 和 [依赖注入指南](dependency-injection.md)
- **安全集成**: 查看 [安全模块文档](SECURITY_README.md) 了解认证授权机制
- **性能优化**: 参考 [限流模块文档](RATELIMIT_README.md) 配置限流策略

---

**📝 维护说明**: 本文档随架构演进持续更新，最后更新时间：2024年12月
**🔗 详细结构**: 完整的项目结构请参考 [项目结构文档](PROJECT_STRUCTURE.md)
