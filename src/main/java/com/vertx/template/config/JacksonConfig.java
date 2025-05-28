package com.vertx.template.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.vertx.core.json.jackson.DatabindCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Jackson配置类，用于配置JSON序列化和反序列化 */
public class JacksonConfig {
  private static final Logger logger = LoggerFactory.getLogger(JacksonConfig.class);
  private static boolean configured = false;

  /** 配置Jackson ObjectMapper以支持Java 8时间类型 */
  public static void configure() {
    if (configured) {
      return;
    }

    try {
      // 获取Vert.x默认的ObjectMapper
      ObjectMapper mapper = DatabindCodec.mapper();

      // 注册JSR310模块以支持Java 8时间类型
      mapper.registerModule(new JavaTimeModule());

      // 禁用将日期写为时间戳的功能，使用ISO-8601格式
      mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

      configured = true;
      logger.info("Jackson配置完成，已启用JSR310时间类型支持");
    } catch (Exception e) {
      logger.error("Jackson配置失败", e);
      throw new RuntimeException("Jackson配置失败", e);
    }
  }

  /** 检查是否已配置 */
  public static boolean isConfigured() {
    return configured;
  }
}
