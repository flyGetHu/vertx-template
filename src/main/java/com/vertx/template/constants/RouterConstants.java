package com.vertx.template.constants;

/**
 * 路由相关常量定义 包含包路径、路由配置等常量
 *
 * @author 系统
 * @since 1.0.0
 */
public final class RouterConstants {

  /** 私有构造函数，防止实例化 */
  private RouterConstants() {
    throw new UnsupportedOperationException("Utility class cannot be instantiated");
  }

  // ==================== 包路径常量 ====================

  /** 基础包路径 */
  public static final String BASE_PACKAGE = "com.vertx.template";

  /** 控制器包路径 */
  public static final String CONTROLLER_PACKAGE = BASE_PACKAGE + ".controller";

  /** 服务包路径 */
  public static final String SERVICE_PACKAGE = BASE_PACKAGE + ".service";

  /** 仓储包路径 */
  public static final String REPOSITORY_PACKAGE = BASE_PACKAGE + ".repository";

  /** 处理器包路径 */
  public static final String HANDLER_PACKAGE = BASE_PACKAGE + ".handler";

  // ==================== 路由配置常量 ====================

  /** 默认路由前缀 */
  public static final String DEFAULT_ROUTE_PREFIX = "/api";

  /** API版本前缀 */
  public static final String API_V1_PREFIX = "/api/v1";

  /** 健康检查路径 */
  public static final String HEALTH_CHECK_PATH = "/health";

  /** 指标监控路径 */
  public static final String METRICS_PATH = "/metrics";

  /** 静态资源路径 */
  public static final String STATIC_RESOURCES_PATH = "/static/*";

  // ==================== 路由处理器配置 ====================

  /** 错误处理器优先级 */
  public static final int ERROR_HANDLER_PRIORITY = 1000;

  /** 全局异常处理器优先级 */
  public static final int GLOBAL_EXCEPTION_HANDLER_PRIORITY = 2000;

  /** 中间件处理器优先级 */
  public static final int MIDDLEWARE_HANDLER_PRIORITY = 100;

  // ==================== 日志消息常量 ====================

  /** 日志消息：路由注册完成 */
  public static final String LOG_ANNOTATION_ROUTES_REGISTERED = "基于注解的路由注册完成";

  /** 日志消息：路由注册失败 */
  public static final String LOG_ANNOTATION_ROUTES_FAILED = "注册基于注解的路由失败";

  /** 日志消息：异常处理器配置完成 */
  public static final String LOG_EXCEPTION_HANDLERS_CONFIGURED = "全局异常处理器配置完成";

  /** 日志消息：中间件注册完成 */
  public static final String LOG_MIDDLEWARES_REGISTERED = "全局中间件注册完成";

  /** 日志消息：路由注册中心初始化完成 */
  public static final String LOG_ROUTER_REGISTRY_INITIALIZED = "路由注册中心初始化完成";
}
