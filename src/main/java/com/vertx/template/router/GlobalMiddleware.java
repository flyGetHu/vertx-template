package com.vertx.template.router;

import com.vertx.template.middleware.auth.AuthMiddleware;
import com.vertx.template.middleware.common.MiddlewareChain;
import com.vertx.template.middleware.ratelimit.RateLimitMiddleware;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 全局中间件配置类 */
@Singleton
public class GlobalMiddleware {
  private static final Logger logger = LoggerFactory.getLogger(GlobalMiddleware.class);
  private final Vertx vertx;
  private final Router router;
  private final JsonObject config;
  private final MiddlewareChain middlewareChain;
  private final AuthMiddleware authMiddleware;
  private final RateLimitMiddleware rateLimitMiddleware;

  /**
   * 构造函数
   *
   * @param vertx Vert.x实例
   * @param router 路由器实例
   * @param config 配置
   * @param middlewareChain 中间件链
   * @param authMiddleware 认证中间件
   * @param rateLimitMiddleware 限流中间件
   */
  @Inject
  public GlobalMiddleware(
      Vertx vertx,
      Router router,
      JsonObject config,
      MiddlewareChain middlewareChain,
      AuthMiddleware authMiddleware,
      RateLimitMiddleware rateLimitMiddleware) {
    this.vertx = vertx;
    this.router = router;
    this.config = config;
    this.middlewareChain = middlewareChain;
    this.authMiddleware = authMiddleware;
    this.rateLimitMiddleware = rateLimitMiddleware;
  }

  /** 注册所有中间件 */
  public void register() {
    // 注册传统的Vert.x处理器
    configureCORS();
    configureBodyHandler();
    configureRequestLogger();

    // 注册自定义中间件到中间件链
    registerCustomMiddlewares();

    logger.info(
        "所有中间件注册完成，共注册了 {} 个自定义中间件: {}",
        middlewareChain.size(),
        middlewareChain.getMiddlewareNames());
  }

  /** 注册自定义中间件到中间件链 */
  private void registerCustomMiddlewares() {
    // 注册认证中间件
    middlewareChain.register(authMiddleware);

    // 注册限流中间件
    middlewareChain.register(rateLimitMiddleware);

    logger.debug("自定义中间件注册完成");
  }

  /**
   * 获取中间件链实例
   *
   * @return 中间件链
   */
  public MiddlewareChain getMiddlewareChain() {
    return middlewareChain;
  }

  /** 配置CORS */
  private void configureCORS() {
    JsonObject corsConfig = config.getJsonObject("cors", new JsonObject());
    if (!corsConfig.getBoolean("enabled", true)) {
      return;
    }

    Set<String> allowedHeaders = new HashSet<>();
    JsonArray headers = corsConfig.getJsonArray("allowed_headers");
    if (headers != null) {
      headers.forEach(header -> allowedHeaders.add((String) header));
    } else {
      allowedHeaders.add("Content-Type");
      allowedHeaders.add("Authorization");
    }

    CorsHandler corsHandler =
        CorsHandler.create().addOrigin(corsConfig.getString("allowed_origins", "*"));

    allowedHeaders.forEach(corsHandler::allowedHeader);

    JsonArray methods = corsConfig.getJsonArray("allowed_methods");
    if (methods != null) {
      methods.forEach(method -> corsHandler.allowedMethod(HttpMethod.valueOf((String) method)));
    } else {
      corsHandler
          .allowedMethod(HttpMethod.GET)
          .allowedMethod(HttpMethod.POST)
          .allowedMethod(HttpMethod.PUT)
          .allowedMethod(HttpMethod.DELETE);
    }

    router.route().handler(corsHandler);
    logger.debug("CORS中间件配置完成");
  }

  /** 配置Body处理器 */
  private void configureBodyHandler() {
    router.route().handler(BodyHandler.create().setBodyLimit(1024 * 1024));
    logger.debug("Body处理器配置完成");
  }

  /** 配置请求日志记录器 */
  private void configureRequestLogger() {
    JsonObject loggingConfig = config.getJsonObject("logging", new JsonObject());
    if (!loggingConfig.getBoolean("request_log", true)) {
      return;
    }

    // 请求计时中间件
    router
        .route()
        .handler(
            ctx -> {
              // 记录请求开始时间
              long startTime = System.currentTimeMillis();

              // 记录请求路径
              String path = ctx.request().path();
              String method = ctx.request().method().name();

              // 请求结束时记录处理时间
              ctx.addEndHandler(
                  ar -> {
                    long duration = System.currentTimeMillis() - startTime;
                    int statusCode = ctx.response().getStatusCode();
                    logger.info(
                        "请求 - 方法:[{}] 路径:{} 状态:{} 耗时:{} ms", method, path, statusCode, duration);
                  });

              ctx.next();
            });

    logger.debug("请求日志中间件配置完成");
  }
}
