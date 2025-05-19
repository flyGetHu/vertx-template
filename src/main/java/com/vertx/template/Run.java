package com.vertx.template;

import com.hazelcast.config.Config;
import com.hazelcast.config.XmlConfigBuilder;
import com.vertx.template.verticle.MainVerticle;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.ThreadingModel;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.spi.cluster.ClusterManager;
import io.vertx.spi.cluster.hazelcast.HazelcastClusterManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * 应用启动入口
 */
public class Run {
  private static final Logger logger = LoggerFactory.getLogger(Run.class);

  public static void main(String[] args) {
    try {
      // 设置VertxOptions，启用虚拟线程
      VertxOptions options = new VertxOptions()
          .setPreferNativeTransport(true) // 优先使用本地传输
          .setEventLoopPoolSize(2 * Runtime.getRuntime().availableProcessors()) // 设置事件循环池大小
          .setWorkerPoolSize(20); // 设置工作线程池大小

      // 加载Hazelcast配置
      boolean enableCluster = System.getProperty("cluster", "true").equals("false");

      if (enableCluster) {
        logger.info("以集群模式启动应用...");
        ClusterManager clusterManager = configureCluster(options);

        // 创建集群Vertx实例并等待完成
        Vertx vertx = Future.await(Vertx.builder()
            .with(options)
            .withClusterManager(clusterManager)
            .buildClustered());

        // 部署MainVerticle
        Future.await(vertx.deployVerticle(new MainVerticle(),
            new DeploymentOptions().setThreadingModel(ThreadingModel.VIRTUAL_THREAD)));
        logger.info("集群模式部署完成，当前节点ID: {}", clusterManager.getNodeId());
      } else {
        logger.info("以单机模式启动应用...");
        // 创建Vertx实例
        Vertx vertx = Vertx.vertx(options);

        // 部署MainVerticle
        Future.await(vertx.deployVerticle(new MainVerticle(),
            new DeploymentOptions().setThreadingModel(ThreadingModel.VIRTUAL_THREAD)));
      }
    } catch (Exception e) {
      logger.error("应用启动失败", e);
      System.exit(1);
    }
  }

  private static ClusterManager configureCluster(VertxOptions options) {
    try {
      // 尝试加载自定义Hazelcast配置
      InputStream is = Run.class.getClassLoader().getResourceAsStream("cluster.xml");
      Config hazelcastConfig;

      if (is != null) {
        logger.info("使用自定义Hazelcast配置文件");
        hazelcastConfig = new XmlConfigBuilder(is).build();
      } else {
        logger.info("使用默认Hazelcast配置");
        hazelcastConfig = new Config();
        hazelcastConfig.setClusterName("vertx-cluster");
      }

      // 创建集群管理器并返回
      return new HazelcastClusterManager(hazelcastConfig);
    } catch (Exception e) {
      logger.error("Hazelcast配置加载失败", e);
      throw e;
    }
  }
}
