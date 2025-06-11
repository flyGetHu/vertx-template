package com.vertx.template.mq.config;

import io.vertx.core.json.JsonObject;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/** 简化的RabbitMQ连接池配置 只保留核心配置参数 */
@Data
@NoArgsConstructor
@Accessors(chain = true)
public class ChannelPoolConfig {

  /** 初始连接池大小 */
  private int initialSize = 5;

  /** 最大连接池大小 */
  private int maxSize = 20;

  /** 从JSON配置创建连接池配置 */
  public static ChannelPoolConfig fromJson(final JsonObject json) {
    if (json == null) {
      return new ChannelPoolConfig();
    }

    return new ChannelPoolConfig()
        .setInitialSize(json.getInteger("initial_size", 5))
        .setMaxSize(json.getInteger("max_size", 20));
  }

  /** 验证配置是否有效 */
  public boolean isValid() {
    return initialSize > 0 && initialSize <= maxSize && maxSize > 0 && maxSize <= 100;
  }
}
