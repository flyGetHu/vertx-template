package com.vertx.template.middleware.common;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;

/**
 * 中间件链管理器 负责管理和执行中间件链
 *
 * @author 系统
 * @since 1.0.0
 */
@Slf4j
@Singleton
public class MiddlewareChain {

  private final List<Middleware> middlewares = new ArrayList<>();

  /**
   * 注册中间件
   *
   * @param middleware 中间件实例
   */
  public void register(final Middleware middleware) {
    if (middleware != null && middleware.isEnabled()) {
      middlewares.add(middleware);
      // 按执行顺序排序
      middlewares.sort(Comparator.comparingInt(Middleware::getOrder));
      log.info(
          "Registered middleware: {} with order: {}", middleware.getName(), middleware.getOrder());
    }
  }

  /**
   * 执行中间件链
   *
   * @param context 路由上下文
   * @return 执行结果
   */
  public Future<Boolean> execute(final RoutingContext context) {
    return executeMiddleware(context, 0);
  }

  /**
   * 递归执行中间件
   *
   * @param context 路由上下文
   * @param index 当前中间件索引
   * @return 执行结果
   */
  private Future<Boolean> executeMiddleware(final RoutingContext context, final int index) {
    if (index >= middlewares.size()) {
      return Future.succeededFuture(true);
    }

    final Middleware middleware = middlewares.get(index);

    try {
      return middleware
          .handle(context)
          .compose(
              result -> {
                if (result) {
                  // 继续执行下一个中间件
                  return executeMiddleware(context, index + 1);
                } else {
                  // 中断执行
                  log.debug("Middleware chain interrupted by: {}", middleware.getName());
                  return Future.succeededFuture(false);
                }
              })
          .recover(
              throwable -> {
                log.error("Error in middleware: {}", middleware.getName(), throwable);
                // 发生错误时继续执行下一个中间件
                return executeMiddleware(context, index + 1);
              });
    } catch (Exception e) {
      log.error("Exception in middleware: {}", middleware.getName(), e);
      // 发生异常时继续执行下一个中间件
      return executeMiddleware(context, index + 1);
    }
  }

  /**
   * 获取已注册的中间件数量
   *
   * @return 中间件数量
   */
  public int size() {
    return middlewares.size();
  }

  /**
   * 获取所有中间件名称
   *
   * @return 中间件名称列表
   */
  public List<String> getMiddlewareNames() {
    return middlewares.stream().map(Middleware::getName).toList();
  }
}
