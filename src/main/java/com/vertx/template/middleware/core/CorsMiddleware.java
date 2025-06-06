package com.vertx.template.middleware.core;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.vertx.template.middleware.common.Middleware;
import com.vertx.template.middleware.common.MiddlewareResult;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.CorsHandler;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CORS中间件
 *
 * <p>处理跨域资源共享(CORS)配置
 *
 * @author 系统
 * @since 1.0.0
 */
@Singleton
public class CorsMiddleware implements Middleware {
  private static final Logger logger = LoggerFactory.getLogger(CorsMiddleware.class);
  private final JsonObject config;
  private CorsHandler corsHandler;
  private boolean enabled;

  @Inject
  public CorsMiddleware(JsonObject config) {
    this.config = config;
    this.initializeCorsHandler();
  }

  /** 初始化CORS处理器 */
  private void initializeCorsHandler() {
    JsonObject corsConfig = config.getJsonObject("cors", new JsonObject());
    this.enabled = corsConfig.getBoolean("enabled", true);

    if (!enabled) {
      logger.info("CORS中间件已禁用");
      return;
    }

    // 配置允许的请求头
    Set<String> allowedHeaders = new HashSet<>();
    JsonArray headers = corsConfig.getJsonArray("allowed_headers");
    if (headers != null) {
      headers.forEach(header -> allowedHeaders.add((String) header));
    } else {
      allowedHeaders.add("Content-Type");
      allowedHeaders.add("Authorization");
    }

    // 创建CORS处理器
    this.corsHandler = CorsHandler.create().addOrigin(corsConfig.getString("allowed_origins", "*"));

    // 添加允许的请求头
    allowedHeaders.forEach(corsHandler::allowedHeader);

    // 配置允许的HTTP方法
    JsonArray methods = corsConfig.getJsonArray("allowed_methods");
    if (methods != null) {
      methods.forEach(method -> corsHandler.allowedMethod(HttpMethod.valueOf((String) method)));
    } else {
      corsHandler
          .allowedMethod(HttpMethod.GET)
          .allowedMethod(HttpMethod.POST)
          .allowedMethod(HttpMethod.PUT)
          .allowedMethod(HttpMethod.DELETE)
          .allowedMethod(HttpMethod.OPTIONS);
    }

    logger.debug("CORS中间件初始化完成");
  }

  @Override
  public MiddlewareResult handle(RoutingContext context) {
    if (!enabled) {
      return MiddlewareResult.success("CORS中间件已禁用");
    }

    try {
      // 使用Vert.x的CorsHandler处理CORS
      corsHandler.handle(context);
      return MiddlewareResult.success("CORS处理完成");
    } catch (Exception e) {
      logger.error("CORS处理失败", e);
      return MiddlewareResult.failure("500", "CORS处理失败: " + e.getMessage());
    }
  }

  @Override
  public String getName() {
    return "CorsMiddleware";
  }

  @Override
  public int getOrder() {
    return 10; // 最高优先级，最先执行
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }
}
