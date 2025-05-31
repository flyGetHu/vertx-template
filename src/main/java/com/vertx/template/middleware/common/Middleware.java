package com.vertx.template.middleware.common;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;

/**
 * 中间件基础接口 定义所有中间件的通用行为
 *
 * @author 系统
 * @since 1.0.0
 */
public interface Middleware {

  /**
   * 处理请求
   *
   * @param context 路由上下文
   * @return 处理结果，包含执行状态、状态码、消息和是否继续执行后续中间件的标识
   */
  Future<MiddlewareResult> handle(RoutingContext context);

  /**
   * 获取中间件名称
   *
   * @return 中间件名称
   */
  String getName();

  /**
   * 获取中间件执行顺序 数值越小，执行优先级越高
   *
   * @return 执行顺序
   */
  default int getOrder() {
    return 100;
  }

  /**
   * 中间件是否启用
   *
   * @return true表示启用，false表示禁用
   */
  default boolean isEnabled() {
    return true;
  }
}
