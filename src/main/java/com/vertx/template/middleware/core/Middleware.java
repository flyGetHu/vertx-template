package com.vertx.template.middleware.core;

import io.vertx.ext.web.RoutingContext;

/** 中间件接口，定义统一的中间件规范 */
public interface Middleware {

  /**
   * 获取中间件名称
   *
   * @return 中间件名称
   */
  String getName();

  /**
   * 获取中间件执行顺序
   *
   * @return 执行顺序，数值越小优先级越高
   */
  int getOrder();

  /**
   * 检查中间件是否启用
   *
   * @return true表示启用，false表示禁用
   */
  boolean isEnabled();

  /**
   * 处理请求
   *
   * @param context 路由上下文
   */
  void handle(RoutingContext context);
}
