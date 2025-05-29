package com.vertx.template.config;

import io.vertx.core.json.JsonObject;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 路由配置类 管理路由相关的配置参数 */
@Getter
public class RouterConfig {
  private static final Logger logger = LoggerFactory.getLogger(RouterConfig.class);

  /** 默认请求体最大大小：1MB */
  private static final long DEFAULT_MAX_REQUEST_BODY_SIZE = 1024 * 1024;

  /** 默认扫描包路径 */
  private static final String DEFAULT_BASE_PACKAGE = "com.vertx.template";

  /** 默认参数长度限制 */
  private static final int DEFAULT_MAX_PARAMETER_LENGTH = 1000;

  private final long maxRequestBodySize;
  private final String basePackage;
  private final int maxParameterLength;
  private final boolean enableParameterValidation;

  public RouterConfig(JsonObject config) {
    JsonObject routerConfig = config.getJsonObject("router", new JsonObject());

    this.maxRequestBodySize =
        routerConfig.getLong("max_request_body_size", DEFAULT_MAX_REQUEST_BODY_SIZE);
    this.basePackage = routerConfig.getString("base_package", DEFAULT_BASE_PACKAGE);
    this.maxParameterLength =
        routerConfig.getInteger("max_parameter_length", DEFAULT_MAX_PARAMETER_LENGTH);
    this.enableParameterValidation = routerConfig.getBoolean("enable_parameter_validation", true);

    logger.info(
        "路由配置加载完成 - 最大请求体大小: {}KB, 扫描包: {}, 最大参数长度: {}, 参数校验: {}",
        maxRequestBodySize / 1024,
        basePackage,
        maxParameterLength,
        enableParameterValidation);
  }

  /** 创建默认配置 */
  public static RouterConfig createDefault() {
    return new RouterConfig(new JsonObject());
  }
}
