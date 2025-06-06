package com.vertx.template.middleware.core.impl;

import com.vertx.template.middleware.core.Middleware;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.CorsHandler;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** CORS中间件实现，处理跨域资源共享配置 */
public class CorsMiddleware implements Middleware {
  private static final Logger logger = LoggerFactory.getLogger(CorsMiddleware.class);

  private static final String NAME = "CorsMiddleware";
  private static final int ORDER = 10; // 最高优先级

  private final boolean enabled;
  private final CorsHandler corsHandler;

  /**
   * 构造函数
   *
   * @param config 应用配置
   */
  public CorsMiddleware(JsonObject config) {
    JsonObject corsConfig = config.getJsonObject("cors", new JsonObject());
    this.enabled = corsConfig.getBoolean("enabled", true);

    if (enabled) {
      this.corsHandler = createCorsHandler(corsConfig);
      logger.info("CORS中间件已启用");
    } else {
      this.corsHandler = null;
      logger.info("CORS中间件已禁用");
    }
  }

  /**
   * 创建CORS处理器
   *
   * @param corsConfig CORS配置
   * @return CorsHandler实例
   */
  private CorsHandler createCorsHandler(JsonObject corsConfig) {
    // 获取允许的源
    String allowedOrigins = corsConfig.getString("allowed_origins", "*");
    CorsHandler handler = CorsHandler.create().addOrigin(allowedOrigins);

    // 配置允许的HTTP方法
    JsonArray allowedMethods = corsConfig.getJsonArray("allowed_methods");
    if (allowedMethods != null) {
      Set<HttpMethod> methods = new HashSet<>();
      for (Object method : allowedMethods) {
        try {
          methods.add(HttpMethod.valueOf(method.toString().toUpperCase()));
        } catch (IllegalArgumentException e) {
          logger.warn("无效的HTTP方法: {}", method);
        }
      }
      methods.forEach(handler::allowedMethod);
    } else {
      // 默认允许的方法
      handler
          .allowedMethod(HttpMethod.GET)
          .allowedMethod(HttpMethod.POST)
          .allowedMethod(HttpMethod.PUT)
          .allowedMethod(HttpMethod.DELETE)
          .allowedMethod(HttpMethod.OPTIONS);
    }

    // 配置允许的请求头
    JsonArray allowedHeaders = corsConfig.getJsonArray("allowed_headers");
    if (allowedHeaders != null) {
      for (Object header : allowedHeaders) {
        handler.allowedHeader(header.toString());
      }
    } else {
      // 默认允许的请求头
      handler
          .allowedHeader("Content-Type")
          .allowedHeader("Authorization")
          .allowedHeader("Access-Control-Allow-Method")
          .allowedHeader("Access-Control-Allow-Origin")
          .allowedHeader("Access-Control-Allow-Credentials");
    }

    // 允许凭证
    boolean allowCredentials = corsConfig.getBoolean("allow_credentials", false);
    if (allowCredentials) {
      handler.allowCredentials(true);
    }

    // 设置预检请求缓存时间
    int maxAge = corsConfig.getInteger("max_age", 86400); // 默认24小时
    handler.maxAgeSeconds(maxAge);

    logger.debug("CORS配置 - 允许源: {}, 允许凭证: {}, 缓存时间: {}秒", allowedOrigins, allowCredentials, maxAge);

    return handler;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public int getOrder() {
    return ORDER;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public void handle(RoutingContext context) {
    if (corsHandler != null) {
      corsHandler.handle(context);
    } else {
      context.next();
    }
  }
}
