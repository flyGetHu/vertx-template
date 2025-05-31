package com.vertx.template.middleware.common;

import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 中间件管理器 负责管理和执行中间件链 使用同步方式执行中间件，简化代码逻辑
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
   * 执行中间件链 使用同步方式执行，简化异步处理逻辑
   *
   * @param context 路由上下文
   * @return 执行结果
   */
  public MiddlewareResult execute(RoutingContext context) {
    if (middlewares.isEmpty()) {
      logger.debug("无中间件需要执行");
      return MiddlewareResult.success("无中间件需要执行");
    }

    logger.debug("开始执行中间件链，共 {} 个中间件", middlewares.size());
    return executeMiddleware(context, 0);
  }

  /**
   * 递归执行中间件 使用同步方式，通过递归调用实现链式执行
   *
   * @param context 路由上下文
   * @param index 当前中间件索引
   * @return 执行结果
   */
  private MiddlewareResult executeMiddleware(RoutingContext context, int index) {
    // 所有中间件执行完成
    if (index >= middlewares.size()) {
      logger.debug("所有中间件执行完成");
      return MiddlewareResult.success("所有中间件执行完成");
    }

    Middleware middleware = middlewares.get(index);
    logger.debug("执行中间件: {} (索引: {})", middleware.getName(), index);

    try {
      // 直接调用中间件的handle方法
      MiddlewareResult result = middleware.handle(context);

      logger.debug(
          "中间件 {} 执行结果: success={}, statusCode={}, message={}, continueChain={}",
          middleware.getName(),
          result.isSuccess(),
          result.getStatusCode(),
          result.getMessage(),
          result.shouldContinueChain());

      // 检查执行结果
      if (!result.isSuccess()) {
        // 中间件执行失败，中断链条
        logger.warn(
            "中间件 {} 执行失败: {} - {}",
            middleware.getName(),
            result.getStatusCode(),
            result.getMessage());
        return result;
      }

      if (!result.shouldContinueChain()) {
        // 中间件要求停止执行链条
        logger.info("中间件 {} 要求停止执行链条: {}", middleware.getName(), result.getMessage());
        return result;
      }

      // 继续执行下一个中间件
      return executeMiddleware(context, index + 1);

    } catch (Exception e) {
      // 中间件执行过程中抛出异常
      logger.error("中间件 {} 执行异常", middleware.getName(), e);
      return MiddlewareResult.failure("500", "中间件执行异常: " + e.getMessage());
    }
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

  /**
   * 获取指定索引的中间件
   *
   * @param index 中间件索引
   * @return 中间件实例，如果索引无效则返回null
   */
  public Middleware getMiddleware(int index) {
    if (index >= 0 && index < middlewares.size()) {
      return middlewares.get(index);
    }
    return null;
  }

  /**
   * 检查是否包含指定名称的中间件
   *
   * @param name 中间件名称
   * @return 如果包含则返回true
   */
  public boolean containsMiddleware(String name) {
    return middlewares.stream().anyMatch(middleware -> middleware.getName().equals(name));
  }
}
