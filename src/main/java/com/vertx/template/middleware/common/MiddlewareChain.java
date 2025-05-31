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
  public Future<MiddlewareResult> execute(final RoutingContext context) {
    if (middlewares.isEmpty()) {
      return Future.succeededFuture(MiddlewareResult.success("无中间件需要执行"));
    }
    return executeMiddleware(context, 0);
  }

  /**
   * 递归执行中间件
   *
   * @param context 路由上下文
   * @param index 当前中间件索引
   * @return 执行结果
   */
  private Future<MiddlewareResult> executeMiddleware(
      final RoutingContext context, final int index) {
    if (index >= middlewares.size()) {
      return Future.succeededFuture(MiddlewareResult.success("所有中间件执行完成"));
    }

    final Middleware middleware = middlewares.get(index);
    log.debug("执行中间件: {}", middleware.getName());

    try {
      return middleware
          .handle(context)
          .compose(
              result -> {
                log.debug("中间件 {} 执行结果: {}", middleware.getName(), result);

                // 检查执行结果
                if (!result.isSuccess()) {
                  // 中间件执行失败，中断链条
                  log.warn(
                      "中间件 {} 执行失败: {} - {}",
                      middleware.getName(),
                      result.getStatusCode(),
                      result.getMessage());
                  return Future.succeededFuture(result);
                }

                if (!result.shouldContinueChain()) {
                  // 中间件要求停止执行链条
                  log.info("中间件 {} 要求停止执行链条: {}", middleware.getName(), result.getMessage());
                  return Future.succeededFuture(result);
                }

                // 继续执行下一个中间件
                return executeMiddleware(context, index + 1);
              })
          .recover(
              throwable -> {
                log.error("Error in middleware: {}", middleware.getName(), throwable);
                // 发生错误时返回错误结果
                MiddlewareResult errorResult =
                    MiddlewareResult.failure("500", "中间件执行异常: " + throwable.getMessage());
                return Future.succeededFuture(errorResult);
              });
    } catch (Exception e) {
      log.error("Exception in middleware: {}", middleware.getName(), e);
      // 发生异常时返回错误结果
      MiddlewareResult errorResult = MiddlewareResult.failure("500", "中间件执行异常: " + e.getMessage());
      return Future.succeededFuture(errorResult);
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
