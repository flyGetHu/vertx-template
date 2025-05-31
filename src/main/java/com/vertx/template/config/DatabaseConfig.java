package com.vertx.template.config;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.mysqlclient.MySQLConnectOptions;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PoolOptions;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 数据库配置类，负责创建和管理数据库连接池 */
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
  @Getter private Pool pool;

  @Inject
  public DatabaseConfig(Vertx vertx, JsonObject config) {
    this.vertx = vertx;
    this.config = config;
    this.initialize();
  }

  /** 初始化数据库连接池 */
  private void initialize() {
    try {
      final JsonObject databaseConfig = config.getJsonObject("database");

      if (databaseConfig == null) {
        logger.warn("数据库配置缺失，跳过数据库初始化");
        return;
      }

      final JsonObject mysqlConfig = databaseConfig.getJsonObject("mysql");

      if (mysqlConfig == null) {
        logger.warn("MySQL配置缺失，跳过数据库初始化");
        return;
      }

      // 数据库连接配置
      final MySQLConnectOptions connectOptions =
          new MySQLConnectOptions()
              .setHost(mysqlConfig.getString("host", "localhost"))
              .setPort(mysqlConfig.getInteger("port", 3306))
              .setDatabase(mysqlConfig.getString("database", "vertx_demo"))
              .setUser(mysqlConfig.getString("username", "root"))
              .setPassword(mysqlConfig.getString("password", "root"))
              .setCharset("utf8mb4");

      // 连接池配置
      final PoolOptions poolOptions =
          new PoolOptions()
              .setMaxLifetime(mysqlConfig.getInteger("max_lifetime", 60000))
              .setMaxWaitQueueSize(mysqlConfig.getInteger("max_wait_queue_size", 100))
              .setMaxSize(mysqlConfig.getInteger("max_pool_size", 5));

      // 创建连接池（使用通用Pool API）
      this.pool = Pool.pool(vertx, connectOptions, poolOptions);
      // 测试
      Future.await(this.pool.query("SELECT 1").execute());
      logger.info("数据库连接池初始化成功");
    } catch (Exception e) {
      logger.error("数据库初始化失败", e);
      throw new RuntimeException("数据库初始化失败", e);
    }
  }

  /** 关闭数据库连接池 */
  public void close() {
    if (pool != null) {
      pool.close();
      logger.info("数据库连接池已关闭");
    }
  }
}
