package com.vertx.template.security;

import io.vertx.ext.web.RoutingContext;

/** 认证器接口，定义认证的统一规范 */
public interface Authenticator {

  /**
   * 执行认证
   *
   * @param ctx 路由上下文
   * @return 认证成功返回用户上下文，失败抛出异常
   * @throws AuthenticationException 认证失败时抛出
   */
  UserContext authenticate(RoutingContext ctx) throws AuthenticationException;
}
