package com.vertx.template.middleware.common;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 中间件管理器 负责管理和执行中间件链
 *
 * @author 系统
 * @since 1.0.0
 */
public class MiddlewareManager {

  private static final Logger logger = LoggerFactory.getLogger(MiddlewareManager.class);

  private final List<Middleware> middlewares = new ArrayList<>();

  /**
   * 注册中间件
   *
   * @param middleware 中间件实例
   */
  public void register(Middleware middleware) {
    if (middleware != null && middleware.isEnabled()) {
      middlewares.add(middleware);
      // 按执行顺序排序
      middlewares.sort(Comparator.comparingInt(Middleware::getOrder));
      logger.info("注册中间件: {} (顺序: {})", middleware.getName(), middleware.getOrder());
    }
  }

  /**
   * 执行中间件链
   *
   * @param context 路由上下文
   * @return 执行结果
   */
  public Future<MiddlewareResult> execute(RoutingContext context) {
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
  private Future<MiddlewareResult> executeMiddleware(RoutingContext context, int index) {
    // 所有中间件执行完成
    if (index >= middlewares.size()) {
      return Future.succeededFuture(MiddlewareResult.success("所有中间件执行完成"));
    }

    Middleware middleware = middlewares.get(index);
    logger.debug("执行中间件: {}", middleware.getName());

    Promise<MiddlewareResult> promise = Promise.promise();

    // 执行当前中间件
    middleware
        .handle(context)
        .onSuccess(
            result -> {
              logger.debug("中间件 {} 执行结果: {}", middleware.getName(), result);

              // 检查执行结果
              if (!result.isSuccess()) {
                // 中间件执行失败，中断链条
                logger.warn(
                    "中间件 {} 执行失败: {} - {}",
                    middleware.getName(),
                    result.getStatusCode(),
                    result.getMessage());
                promise.complete(result);
                return;
              }

              if (!result.shouldContinueChain()) {
                // 中间件要求停止执行链条
                logger.info("中间件 {} 要求停止执行链条: {}", middleware.getName(), result.getMessage());
                promise.complete(result);
                return;
              }

              // 继续执行下一个中间件
              executeMiddleware(context, index + 1)
                  .onSuccess(promise::complete)
                  .onFailure(promise::fail);
            })
        .onFailure(
            throwable -> {
              // 中间件执行过程中抛出异常
              logger.error("中间件 {} 执行异常", middleware.getName(), throwable);
              MiddlewareResult errorResult =
                  MiddlewareResult.failure("500", "中间件执行异常: " + throwable.getMessage());
              promise.complete(errorResult);
            });

    return promise.future();
  }

  /**
   * 获取已注册的中间件数量
   *
   * @return 中间件数量
   */
  public int getMiddlewareCount() {
    return middlewares.size();
  }

  /**
   * 获取已注册的中间件列表
   *
   * @return 中间件列表
   */
  public List<String> getMiddlewareNames() {
    return middlewares.stream().map(Middleware::getName).toList();
  }

  /** 清空所有中间件 */
  public void clear() {
    middlewares.clear();
    logger.info("清空所有中间件");
  }
}
