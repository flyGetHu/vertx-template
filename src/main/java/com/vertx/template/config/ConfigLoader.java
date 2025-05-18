package com.vertx.template.config;

import io.vertx.config.ConfigRetriever;
import io.vertx.config.ConfigRetrieverOptions;
import io.vertx.config.ConfigStoreOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 配置加载器，负责从YAML文件加载配置
 */
public class ConfigLoader {
  private static final Logger logger = LoggerFactory.getLogger(ConfigLoader.class);
  private static JsonObject cachedConfig;

  /**
   * 加载配置
   *
   * @param vertx Vertx实例
   * @return 返回加载的配置对象的Future
   */
  public static Future<JsonObject> loadConfig(Vertx vertx) {
    if (cachedConfig != null) {
      return Future.succeededFuture(cachedConfig);
    }

    try {
      // 创建File配置源
      ConfigStoreOptions fileStore = new ConfigStoreOptions()
          .setType("file")
          .setFormat("yaml")
          .setConfig(new JsonObject().put("path", "config.yml"));

      // 配置环境变量和系统属性作为覆盖
      ConfigStoreOptions envStore = new ConfigStoreOptions()
          .setType("env");
      ConfigStoreOptions sysStore = new ConfigStoreOptions()
          .setType("sys");

      // 创建ConfigRetriever
      ConfigRetrieverOptions options = new ConfigRetrieverOptions()
          .addStore(fileStore)
          .addStore(envStore)
          .addStore(sysStore);

      ConfigRetriever retriever = ConfigRetriever.create(vertx, options);

      // 使用await直接获取配置结果
      JsonObject config = Future.await(retriever.getConfig());
      cachedConfig = config;
      logger.info("配置加载成功");

      return Future.succeededFuture(config);
    } catch (Exception e) {
      logger.error("配置加载失败: " + e.getMessage());
      return Future.failedFuture(e);
    }
  }

  /**
   * 获取已加载的配置
   *
   * @return 配置对象，如果未加载则返回空对象
   */
  public static JsonObject getConfig() {
    return cachedConfig != null ? cachedConfig : new JsonObject();
  }
}
