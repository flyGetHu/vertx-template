package com.vertx.template.config;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Pool;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.sqlclient.PoolOptions;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * 数据库配置类，负责创建和管理数据库连接池
 */
@Singleton
public class DatabaseConfig {
  private static final Logger logger = LoggerFactory.getLogger(DatabaseConfig.class);
  private final Vertx vertx;
  private final JsonObject config;
  /**
   * -- GETTER -- 获取数据库连接池
   *
   * @return 通用SQL客户端连接池
   */
  @Getter
  private Pool pool;

  @Inject
  public DatabaseConfig(Vertx vertx, JsonObject config) {
    this.vertx = vertx;
    this.config = config;
    this.initialize();
  }

  /**
   * 初始化数据库连接池
   */
  private void initialize() {
    try {
      final JsonObject dbConfig = config.getJsonObject("database");

      if (dbConfig == null) {
        logger.warn("数据库配置缺失，跳过数据库初始化");
        return;
      }

      // 数据库连接配置
      final MySQLConnectOptions connectOptions = new MySQLConnectOptions()
          .setHost(dbConfig.getString("host", "localhost")).setPort(dbConfig.getInteger("port", 3306))
          .setDatabase(dbConfig.getString("database", "vertx_demo")).setUser(dbConfig.getString("username", "root"))
          .setPassword(dbConfig.getString("password", "root"))
          .setConnectTimeout(dbConfig.getInteger("connect_timeout", 10000))
          .setIdleTimeout(dbConfig.getInteger("idle_timeout", 30000));

      // 连接池配置
      final PoolOptions poolOptions = new PoolOptions().setMaxLifetime(dbConfig.getInteger("max_lifetime", 60000))
          .setMaxWaitQueueSize(dbConfig.getInteger("max_wait_queue_size", 100))
          .setMaxSize(dbConfig.getInteger("max_pool_size", 5));

      // 创建连接池（使用通用Pool API）
      this.pool = Pool.pool(vertx, connectOptions, poolOptions);

      logger.info("数据库连接池初始化成功");
    } catch (Exception e) {
      logger.error("数据库初始化失败", e);
    }
  }

  /**
   * 关闭数据库连接池
   */
  public void close() {
    if (pool != null) {
      pool.close();
      logger.info("数据库连接池已关闭");
    }
  }
}
