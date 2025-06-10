package com.vertx.template.middleware.core;

import com.vertx.template.config.ConfigLoader;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.LoggerFormat;
import io.vertx.ext.web.handler.LoggerHandler;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 中间件链管理器，负责注册和管理中间件的执行顺序 */
public class MiddlewareChain {
  private static final Logger logger = LoggerFactory.getLogger(MiddlewareChain.class);

  private final List<Middleware> middlewares = new ArrayList<>();

  /**
   * 注册中间件
   *
   * @param middleware 要注册的中间件
   */
  public void register(Middleware middleware) {
    if (middleware.isEnabled()) {
      middlewares.add(middleware);
      logger.info("注册中间件: {} (order: {})", middleware.getName(), middleware.getOrder());
    } else {
      logger.debug("跳过禁用的中间件: {}", middleware.getName());
    }
  }

  /**
   * 将所有中间件应用到路由器
   *
   * @param router 路由器实例
   */
  public void applyTo(Router router) {
    // 按优先级排序（order值越小优先级越高）
    middlewares.sort(Comparator.comparingInt(Middleware::getOrder));

    final Route route = router.route();

    // 根据配置添加日志中间件
    addLoggerMiddleware(route);

    // 应用中间件到路由器
    for (Middleware middleware : middlewares) {
      route.handler(middleware::handle);
      logger.debug("应用中间件: {} (order: {})", middleware.getName(), middleware.getOrder());
    }

    logger.info("已应用 {} 个中间件到路由器", middlewares.size());
  }

  /**
   * 根据配置添加日志中间件
   *
   * @param route 路由实例
   */
  private void addLoggerMiddleware(Route route) {
    final JsonObject config = ConfigLoader.getConfig();
    final JsonObject loggingConfig = config.getJsonObject("logging", new JsonObject());
    final JsonObject middlewareConfig = loggingConfig.getJsonObject("middleware", new JsonObject());

    // 检查是否启用日志中间件（默认启用）
    final boolean enabled = middlewareConfig.getBoolean("enabled", true);
    if (!enabled) {
      logger.debug("日志中间件已禁用");
      return;
    }

    // 获取配置参数
    final boolean immediate = middlewareConfig.getBoolean("immediate", true);
    final String formatStr = middlewareConfig.getString("format", "SHORT");

    // 解析日志格式
    LoggerFormat format;
    try {
      format = LoggerFormat.valueOf(formatStr.toUpperCase());
    } catch (IllegalArgumentException e) {
      logger.warn("无效的日志格式: {}, 使用默认格式 SHORT", formatStr);
      format = LoggerFormat.SHORT;
    }

    // 添加日志中间件
    route.handler(LoggerHandler.create(immediate, format));
    logger.info("已启用HTTP请求日志中间件 - immediate: {}, format: {}", immediate, format);
  }

  /**
   * 获取已注册的中间件数量
   *
   * @return 中间件数量
   */
  public int size() {
    return middlewares.size();
  }

  /** 清空所有中间件 */
  public void clear() {
    middlewares.clear();
    logger.debug("清空所有中间件");
  }
}
