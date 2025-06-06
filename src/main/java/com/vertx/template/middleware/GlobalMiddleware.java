package com.vertx.template.middleware;

import com.vertx.template.middleware.core.MiddlewareChain;
import com.vertx.template.middleware.core.impl.CorsMiddleware;
import io.vertx.ext.web.Router;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 全局中间件管理器，负责注册和管理核心中间件 */
@Singleton
public class GlobalMiddleware {
  private static final Logger logger = LoggerFactory.getLogger(GlobalMiddleware.class);

  private final MiddlewareChain middlewareChain;
  private final CorsMiddleware corsMiddleware;

  /**
   * 构造函数
   *
   * @param corsMiddleware CORS中间件
   */
  @Inject
  public GlobalMiddleware(CorsMiddleware corsMiddleware) {
    this.middlewareChain = new MiddlewareChain();
    this.corsMiddleware = corsMiddleware;

    // 注册核心中间件
    registerCoreMiddlewares();
  }

  /** 注册核心中间件 */
  private void registerCoreMiddlewares() {
    // 1. CORS中间件 (order=10) - 最高优先级
    if (corsMiddleware.isEnabled()) {
      middlewareChain.register(corsMiddleware);
    }

    // 注意：这里可以继续添加其他核心中间件
    // 2. Body处理器中间件 (order=20)
    // 3. 请求日志中间件 (order=30)

    logger.info("核心中间件注册完成，共注册 {} 个中间件", middlewareChain.size());
  }

  /**
   * 将所有中间件应用到路由器
   *
   * @param router 路由器实例
   */
  public void applyTo(Router router) {
    middlewareChain.applyTo(router);
    logger.info("全局中间件已应用到路由器");
  }

  /**
   * 获取中间件链
   *
   * @return 中间件链实例
   */
  public MiddlewareChain getMiddlewareChain() {
    return middlewareChain;
  }
}
