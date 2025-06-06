package com.vertx.template.router.scanner;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.vertx.template.config.RouterConfig;
import com.vertx.template.exception.RouteRegistrationException;
import com.vertx.template.router.annotation.RestController;
import java.util.Set;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 路由扫描器，专门负责扫描和发现带有路由注解的控制器 @功能描述 扫描指定包下的@RestController注解类 @职责范围 类扫描、注解检测、控制器发现 */
@Singleton
public class RouteScanner {

  private static final Logger logger = LoggerFactory.getLogger(RouteScanner.class);

  private final RouterConfig routerConfig;

  @Inject
  public RouteScanner(RouterConfig routerConfig) {
    this.routerConfig = routerConfig;
  }

  /**
   * 扫描控制器类
   *
   * @return 带有@RestController注解的类集合
   * @throws RouteRegistrationException 扫描失败时抛出
   */
  public Set<Class<?>> scanControllers() {
    try {
      logger.info("开始扫描控制器，扫描包: {}", routerConfig.getBasePackage());

      Reflections reflections = new Reflections(routerConfig.getBasePackage());
      Set<Class<?>> controllerClasses = reflections.getTypesAnnotatedWith(RestController.class);

      if (controllerClasses.isEmpty()) {
        logger.warn("未找到任何带有@RestController注解的控制器类，请检查包路径配置");
      } else {
        logger.info("扫描完成，找到 {} 个控制器类", controllerClasses.size());
      }

      return controllerClasses;

    } catch (Exception e) {
      String errorMsg = "扫描控制器时发生异常: " + e.getMessage();
      logger.error(errorMsg, e);
      throw new RouteRegistrationException(errorMsg, e);
    }
  }

  /**
   * 检查类是否为有效的控制器
   *
   * @param clazz 要检查的类
   * @return 是否为有效控制器
   */
  public boolean isValidController(Class<?> clazz) {
    return clazz.isAnnotationPresent(RestController.class);
  }
}
