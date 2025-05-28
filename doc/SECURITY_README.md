# 安全认证系统使用指南

本项目实现了一个简单、直接、高效的安全认证系统，支持多种认证方式，默认使用JWT认证。

## 功能特性

- **默认认证**: 所有接口默认需要JWT认证，无需显式添加注解
- **注解驱动**: 使用 `@RequireAuth` 注解指定特定的认证类型或跳过认证
- **多种认证类型**: 支持JWT、自定义认证、无认证等多种认证方式
- **用户上下文**: 通过 `@CurrentUser` 注解自动注入当前用户信息
- **灵活配置**: 支持方法级和类级认证配置
- **异常处理**: 统一的认证异常处理机制

## 核心组件

### 1. 认证注解

#### @RequireAuth
标记需要认证的方法或类：
```java
@RequireAuth(AuthType.JWT)  // 使用JWT认证
@RequireAuth(AuthType.CUSTOM)  // 使用自定义认证
```

#### @CurrentUser
注入当前用户上下文：
```java
public JsonObject getUserInfo(@CurrentUser UserContext userContext) {
    String userId = userContext.getUserId();
    // ...
}
```

### 2. 认证类型

- `AuthType.JWT`: JWT认证（默认）
- `AuthType.CUSTOM`: 自定义认证实现
- `AuthType.NONE`: 不需要认证（空实现）

### 3. 核心类

- `AuthenticationManager`: 认证管理器，负责选择和执行认证
- `JwtAuthenticator`: JWT认证器实现
- `UserContext`: 用户上下文，保存用户ID
- `JwtUtils`: JWT工具类，用于生成和验证token

## 使用示例

### 1. 控制器中使用认证

```java
@RestController
@RequestMapping("/api/users")
public class UserController {

  // 公开接口（不需要认证）
  @GetMapping("/public/info")
  @RequireAuth(AuthType.NONE)
  public JsonObject publicEndpoint() {
    return new JsonObject().put("message", "公开接口");
  }

  // 默认需要JWT认证的接口（无需添加注解）
  @GetMapping("/profile")
  public JsonObject getUserProfile(@CurrentUser UserContext userContext) {
    return new JsonObject()
        .put("userId", userContext.getUserId())
        .put("username", userContext.getUsername());
  }

  // 需要自定义认证的接口
  @PostMapping("/admin")
  @RequireAuth(AuthType.CUSTOM)
  public JsonObject adminOperation() {
    return new JsonObject().put("message", "管理员操作完成");
  }

  // 类级别认证配置
  @RequireAuth(AuthType.NONE)
  @RequestMapping("/api/public")
  public class PublicController {
    // 该控制器下的所有方法都不需要认证
  }
}
```

### 2. 获取JWT Token

```bash
# 登录获取token
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"password"}'

# 响应
{
  "success": true,
  "message": "登录成功",
  "token": "eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9...",
  "userId": "user_testuser",
  "timestamp": 1703123456789
}
```

### 3. 使用Token访问受保护接口

```bash
# 访问需要认证的接口
curl -X GET http://localhost:8080/api/users/profile \
  -H "Authorization: Bearer eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9..."

# 响应
{
  "message": "获取用户信息成功",
  "userId": "user_testuser",
  "timestamp": 1703123456789
}
```

## JWT配置

在配置文件中设置JWT密钥：

```json
{
  "jwt": {
    "secret": "your-secret-key-here"
  }
}
```

## 系统特点

1. **默认安全**: 所有接口默认需要认证，提高系统安全性
2. **零配置启动**: 系统启动时自动注册所有认证器
3. **注解驱动**: 通过注解声明式配置认证需求
4. **类型安全**: 强类型的用户上下文和认证类型
5. **扩展性强**: 支持自定义认证器实现
6. **异常统一**: 统一的认证异常处理机制
7. **灵活配置**: 支持跳过认证的公开接口
8. **安全优先**: 采用白名单机制，需要显式声明公开接口

## 认证流程

1. **默认认证**: 所有接口默认需要JWT认证
2. **注解覆盖**: 通过 `@RequireAuth` 注解可以指定其他认证类型或跳过认证
3. **认证器选择**: 根据 `AuthType` 选择对应的认证器
4. **认证执行**: 执行具体的认证逻辑（NONE类型跳过认证）
5. **用户上下文**: 认证成功后，将用户信息存储到上下文中
6. **参数注入**: 通过 `@CurrentUser` 注解自动注入用户信息

## JWT过期时间偏移

JWT认证器支持5分钟的过期时间偏移，即token在标准过期时间后的5分钟内仍然有效，提供更好的用户体验。

## 自定义认证器

实现`Authenticator`接口来创建自定义认证器：

```java
@Singleton
public class CustomAuthenticator implements Authenticator {

  @Override
  public UserContext authenticate(RoutingContext ctx) throws AuthenticationException {
    // 自定义认证逻辑
    String customToken = ctx.request().getHeader("X-Custom-Token");

    if (customToken == null) {
      throw new AuthenticationException("缺少自定义token");
    }

    // 验证token并返回用户上下文
    String userId = validateCustomToken(customToken);
    return new UserContext(userId);
  }
}
```

然后注册到认证管理器：

```java
authenticationManager.registerAuthenticator(AuthType.CUSTOM, customAuthenticator);
```

## 错误处理

认证失败时会抛出`AuthenticationException`，系统会自动返回401状态码和错误信息。

## 测试接口

项目提供了以下测试接口：

- `POST /api/auth/login` - 用户登录
- `POST /api/auth/test-token` - 生成测试token
- `GET /api/users/public` - 公开接口（无需认证）
- `GET /api/users/profile` - 需要认证的接口
- `POST /api/users/profile` - 需要认证的POST接口

## 安全建议

1. 在生产环境中更改默认的JWT密钥
2. 设置合适的token过期时间
3. 使用HTTPS传输token
4. 定期轮换JWT密钥
5. 实现token刷新机制
