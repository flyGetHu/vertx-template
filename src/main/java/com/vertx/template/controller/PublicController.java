package com.vertx.template.controller;

import com.vertx.template.router.annotation.*;
import com.vertx.template.security.annotation.AuthType;
import com.vertx.template.security.annotation.RequireAuth;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 公开控制器 - 所有接口都不需要认证 */
@RestController
@RequestMapping("/api/public")
@RequireAuth(AuthType.NONE) // 类级别配置，所有方法都不需要认证
@Singleton
public class PublicController {
  private static final Logger logger = LoggerFactory.getLogger(PublicController.class);

  /** 获取系统状态 */
  @GetMapping("/status")
  public Map<String, Object> getSystemStatus() {
    logger.debug("获取系统状态");
    Map<String, Object> status = new HashMap<>();
    status.put("status", "running");
    status.put("timestamp", System.currentTimeMillis());
    status.put("version", "1.0.0");
    return status;
  }

  /** 获取API文档信息 */
  @GetMapping("/docs")
  public Map<String, Object> getApiDocs() {
    logger.debug("获取API文档信息");
    Map<String, Object> docs = new HashMap<>();
    docs.put("title", "Vert.x Template API");
    docs.put("version", "1.0.0");
    docs.put("description", "基于Vert.x的模板项目API文档");
    return docs;
  }

  /** 健康检查接口 */
  @GetMapping("/health")
  public Map<String, Object> healthCheck() {
    logger.debug("执行健康检查");
    Map<String, Object> health = new HashMap<>();
    health.put("status", "UP");
    health.put("timestamp", System.currentTimeMillis());
    health.put(
        "checks",
        new HashMap<String, String>() {
          {
            put("database", "UP");
            put("memory", "UP");
          }
        });
    return health;
  }
}
