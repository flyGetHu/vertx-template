package com.vertx.template.router;

import io.vertx.ext.web.Router;

/**
 * 路由组接口，所有路由组都应该实现此接口
 */
public interface RouteGroup {

  /**
   * 路由注册方法
   *
   * @param router 路由器实例
   */
  void register(Router router);
}
