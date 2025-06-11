package com.vertx.template.verticle;

import com.vertx.template.config.ConfigLoader;
import com.vertx.template.config.JacksonConfig;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.ThreadingModel;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 主Verticle 负责应用初始化、配置加载和其他Verticle的部署协调 */
public class MainVerticle extends AbstractVerticle {
  private static final Logger logger = LoggerFactory.getLogger(MainVerticle.class);

  @Override
  public void start(Promise<Void> startPromise) {
    try {
      // 配置Jackson以支持Java 8时间类型
      JacksonConfig.configure();

      // 加载配置（使用await直接获取结果）
      final JsonObject config = Future.await(ConfigLoader.loadConfig(vertx));

      // 部署MqVerticle - 可以多实例部署用于并行处理消息
      final DeploymentOptions mqOptions = new DeploymentOptions();
      mqOptions.setConfig(config);
      mqOptions.setThreadingModel(ThreadingModel.VIRTUAL_THREAD);
      mqOptions.setInstances(3); // MQ消费者可以有多个实例

      Future.await(vertx.deployVerticle(MqVerticle.class, mqOptions));

      // 部署WebVerticle - 只需要1个实例（HTTP服务器）
      final DeploymentOptions webOptions = new DeploymentOptions();
      webOptions.setConfig(config);
      webOptions.setThreadingModel(ThreadingModel.VIRTUAL_THREAD);
      webOptions.setInstances(1); // Web服务器只需要1个实例

      Future.await(vertx.deployVerticle(WebVerticle.class, webOptions));

      // 启动成功
      logger.info("MainVerticle启动成功 - 所有Verticle部署完成");
      startPromise.complete();
    } catch (Exception e) {
      logger.error("MainVerticle启动失败：", e);
      startPromise.fail(e);
    }
  }
}
