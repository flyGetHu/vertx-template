package com.vertx.template.middleware.auth;

import com.vertx.template.middleware.common.Middleware;
import io.vertx.ext.web.RoutingContext;

/**
 * 认证中间件接口 定义认证相关的中间件行为
 *
 * @author 系统
 * @since 1.0.0
 */
public interface AuthMiddleware extends Middleware {

  /**
   * 验证用户身份
   *
   * @param context 路由上下文
   * @return 验证结果，true表示验证通过，false表示验证失败
   */
  boolean authenticate(RoutingContext context);

  /**
   * 获取当前用户ID
   *
   * @param context 路由上下文
   * @return 用户ID，如果未认证返回null
   */
  String getCurrentUserId(RoutingContext context);

  /**
   * 检查用户权限
   *
   * @param context 路由上下文
   * @param permission 所需权限
   * @return 权限检查结果
   */
  boolean hasPermission(RoutingContext context, String permission);

  @Override
  default int getOrder() {
    return 10; // 认证中间件优先级较高
  }
}
