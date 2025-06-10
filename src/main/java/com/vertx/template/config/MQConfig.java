package com.vertx.template.config;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.vertx.template.mq.config.RabbitMqConfig;
import io.vertx.core.json.JsonObject;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Getter
public class MQConfig {
  private static final Logger logger = LoggerFactory.getLogger(MQConfig.class);
  private RabbitMqConfig rabbitMQConfig;

  @Inject
  public MQConfig(JsonObject config) {
    initialize(config);
  }

  private void initialize(JsonObject config) {
    try {
      final JsonObject mqConfig = config.getJsonObject("mq");

      if (mqConfig == null) {
        logger.warn(
            "MQ configuration ('mq') is missing from the config file, using default MQ settings.");
        this.rabbitMQConfig = new RabbitMqConfig(); // Use default config
        return;
      }

      this.rabbitMQConfig = RabbitMqConfig.fromJson(mqConfig.getJsonObject("rabbitmq"));
      logger.info("MQ configuration loaded successfully.");

    } catch (Exception e) {
      logger.error("Failed to initialize MQ configuration", e);
      throw new RuntimeException("Failed to initialize MQ configuration", e);
    }
  }
}
