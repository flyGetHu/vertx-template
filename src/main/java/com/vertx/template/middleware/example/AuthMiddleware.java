package com.vertx.template.middleware.example;

import com.vertx.template.middleware.common.Middleware;
import com.vertx.template.middleware.common.MiddlewareResult;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;

/**
 * 认证中间件示例 演示如何使用新的MiddlewareResult处理各种情况
 *
 * @author 系统
 * @since 1.0.0
 */
public class AuthMiddleware implements Middleware {

  @Override
  public Future<MiddlewareResult> handle(RoutingContext context) {
    try {
      String token = context.request().getHeader("Authorization");

      // 检查token是否存在
      if (token == null || token.trim().isEmpty()) {
        return Future.succeededFuture(MiddlewareResult.failure("401", "缺少认证令牌"));
      }

      // 检查token格式
      if (!token.startsWith("Bearer ")) {
        return Future.succeededFuture(MiddlewareResult.failure("401", "认证令牌格式错误"));
      }

      String actualToken = token.substring(7);

      // 模拟token验证
      if ("invalid_token".equals(actualToken)) {
        return Future.succeededFuture(MiddlewareResult.failure("401", "认证令牌无效"));
      }

      if ("expired_token".equals(actualToken)) {
        return Future.succeededFuture(MiddlewareResult.failure("401", "认证令牌已过期"));
      }

      // 认证成功，继续执行后续中间件
      context.put("userId", "user123");
      context.put("userRole", "admin");

      return Future.succeededFuture(MiddlewareResult.success("认证成功", "user123"));

    } catch (Exception e) {
      // 对于严重错误，可以返回失败的Future
      return Future.failedFuture(e);
      // 或者返回包装的错误结果
      // return Future.succeededFuture(
      // MiddlewareResult.failure("500", "认证过程发生内部错误: " + e.getMessage())
      // );
    }
  }

  @Override
  public String getName() {
    return "AuthMiddleware";
  }

  @Override
  public int getOrder() {
    return 10; // 认证中间件优先级较高
  }
}
