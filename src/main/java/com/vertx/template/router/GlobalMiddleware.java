package com.vertx.template.router;

import javax.inject.Inject;
import javax.inject.Singleton;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * 全局中间件配置类
 */
@Singleton
public class GlobalMiddleware {
  private static final Logger logger = LoggerFactory.getLogger(GlobalMiddleware.class);
  private final Vertx vertx;
  private final Router router;
  private final JsonObject config;

  /**
   * 构造函数
   *
   * @param vertx  Vert.x实例
   * @param router 路由器实例
   * @param config 配置
   */
  @Inject
  public GlobalMiddleware(Vertx vertx, Router router, JsonObject config) {
    this.vertx = vertx;
    this.router = router;
    this.config = config;
  }

  /**
   * 注册所有中间件
   */
  public void register() {
    configureCORS();
    configureBodyHandler();
    configureRequestLogger();
  }

  /**
   * 配置CORS
   */
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

    CorsHandler corsHandler = CorsHandler.create()
        .addOrigin(corsConfig.getString("allowed_origins", "*"));

    allowedHeaders.forEach(corsHandler::allowedHeader);

    JsonArray methods = corsConfig.getJsonArray("allowed_methods");
    if (methods != null) {
      methods.forEach(method -> corsHandler.allowedMethod(HttpMethod.valueOf((String) method)));
    } else {
      corsHandler.allowedMethod(HttpMethod.GET)
          .allowedMethod(HttpMethod.POST)
          .allowedMethod(HttpMethod.PUT)
          .allowedMethod(HttpMethod.DELETE);
    }

    router.route().handler(corsHandler);
    logger.debug("CORS中间件配置完成");
  }

  /**
   * 配置Body处理器
   */
  private void configureBodyHandler() {
    router.route().handler(BodyHandler.create().setBodyLimit(1024 * 1024));
    logger.debug("Body处理器配置完成");
  }

  /**
   * 配置请求日志记录器
   */
  private void configureRequestLogger() {
    JsonObject loggingConfig = config.getJsonObject("logging", new JsonObject());
    if (!loggingConfig.getBoolean("request_log", true)) {
      return;
    }

    // 请求计时中间件
    router.route().handler(ctx -> {
      // 记录请求开始时间
      long startTime = System.currentTimeMillis();

      // 记录请求路径
      String path = ctx.request().path();
      String method = ctx.request().method().name();

      // 请求结束时记录处理时间
      ctx.addEndHandler(ar -> {
        long duration = System.currentTimeMillis() - startTime;
        int statusCode = ctx.response().getStatusCode();
        logger.info("请求 - 方法:[{}] 路径:{} 状态:{} 耗时:{} ms", method, path, statusCode, duration);
      });

      ctx.next();
    });

    logger.debug("请求日志中间件配置完成");
  }
}
