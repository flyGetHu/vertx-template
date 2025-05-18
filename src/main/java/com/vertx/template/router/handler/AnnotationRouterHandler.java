package com.vertx.template.router.handler;

import com.google.inject.Injector;
import com.vertx.template.handler.ResponseHandler;
import com.vertx.template.router.annotation.*;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Set;

/**
 * 注解路由处理器，负责扫描并注册带有路由注解的控制器方法
 */
@Singleton
public class AnnotationRouterHandler {
  private static final Logger logger = LoggerFactory.getLogger(AnnotationRouterHandler.class);
  private final Injector injector;
  private final ResponseHandler responseHandler;
  private static final String BASE_PACKAGE = "com.vertx.template";

  @Inject
  public AnnotationRouterHandler(Injector injector, ResponseHandler responseHandler) {
    this.injector = injector;
    this.responseHandler = responseHandler;
  }

  /**
   * 注册所有带有路由注解的控制器方法
   *
   * @param router 路由器
   */
  public void registerRoutes(Router router) {
    try {
      Reflections reflections = new Reflections(BASE_PACKAGE);
      Set<Class<?>> controllerClasses = reflections.getTypesAnnotatedWith(RestController.class);

      for (Class<?> controllerClass : controllerClasses) {
        registerController(router, controllerClass);
      }

      logger.info("基于注解的路由注册完成，共扫描 {} 个控制器", controllerClasses.size());
    } catch (Exception e) {
      logger.error("注册注解路由时发生异常", e);
    }
  }

  /**
   * 注册单个控制器类中的所有路由方法
   *
   * @param router          路由器
   * @param controllerClass 控制器类
   */
  private void registerController(Router router, Class<?> controllerClass) {
    try {
      // 获取控制器实例
      Object controller = injector.getInstance(controllerClass);

      // 获取类级别的RequestMapping注解
      String basePath = "";
      if (controllerClass.isAnnotationPresent(RequestMapping.class)) {
        RequestMapping requestMapping = controllerClass.getAnnotation(RequestMapping.class);
        basePath = requestMapping.value();
      }

      // 处理所有方法
      for (Method method : controllerClass.getMethods()) {
        processMethod(router, controller, method, basePath);
      }

      logger.debug("控制器 [{}] 路由注册完成", controllerClass.getSimpleName());
    } catch (Exception e) {
      logger.error("注册控制器 [{}] 路由时发生异常", controllerClass.getSimpleName(), e);
    }
  }

  /**
   * 处理控制器方法，注册对应的路由
   *
   * @param router     路由器
   * @param controller 控制器实例
   * @param method     控制器方法
   * @param basePath   基础路径
   */
  private void processMethod(Router router, Object controller, Method method, String basePath) {
    // 处理各种HTTP方法注解
    if (method.isAnnotationPresent(GetMapping.class)) {
      GetMapping mapping = method.getAnnotation(GetMapping.class);
      String path = combinePath(basePath, mapping.value());
      Route route = router.get(path);
      registerHandler(route, controller, method);
      logger.debug("注册GET路由: {}", path);
    } else if (method.isAnnotationPresent(PostMapping.class)) {
      PostMapping mapping = method.getAnnotation(PostMapping.class);
      String path = combinePath(basePath, mapping.value());
      Route route = router.post(path);
      registerHandler(route, controller, method);
      logger.debug("注册POST路由: {}", path);
    } else if (method.isAnnotationPresent(RequestMapping.class)) {
      RequestMapping mapping = method.getAnnotation(RequestMapping.class);
      String path = combinePath(basePath, mapping.value());
      Route route;

      if (mapping.method().length == 0) {
        // 如果未指定方法，则默认注册所有方法
        route = router.route(path);
      } else {
        // 注册指定的HTTP方法
        route = router.route(path);
        for (HttpMethod httpMethod : mapping.method()) {
          route.method(convertHttpMethod(httpMethod));
        }
      }

      registerHandler(route, controller, method);
      logger.debug("注册通用路由: {}", path);
    }
  }

  /**
   * 将自定义HttpMethod转换为Vert.x的HttpMethod
   */
  private io.vertx.core.http.HttpMethod convertHttpMethod(HttpMethod method) {
    return io.vertx.core.http.HttpMethod.valueOf(method.name());
  }

  /**
   * 合并基础路径和方法路径
   */
  private String combinePath(String basePath, String methodPath) {
    if (basePath.isEmpty()) {
      return methodPath;
    }

    if (methodPath.isEmpty()) {
      return basePath;
    }

    if (basePath.endsWith("/") && methodPath.startsWith("/")) {
      return basePath + methodPath.substring(1);
    } else if (!basePath.endsWith("/") && !methodPath.startsWith("/")) {
      return basePath + "/" + methodPath;
    } else {
      return basePath + methodPath;
    }
  }

  /**
   * 为路由注册处理器
   */
  private void registerHandler(Route route, Object controller, Method method) {
    Handler<RoutingContext> handler = createHandler(controller, method);
    route.handler(handler);
  }

  /**
   * 创建路由处理器
   */
  private Handler<RoutingContext> createHandler(Object controller, Method method) {
    return responseHandler.handle(ctx -> {
      try {
        // 判断方法参数类型
        Object[] args = resolveMethodArgs(method, ctx);

        // 调用控制器方法
        Object result = method.invoke(controller, args);

        // 处理Future结果
        if (result instanceof Future) {
          return Future.await((Future<?>) result);
        }

        return result;
      } catch (Exception e) {
        logger.error("调用控制器方法时发生异常", e);
        throw new RuntimeException(e);
      }
    });
  }

  /**
   * 解析方法参数
   */
  private Object[] resolveMethodArgs(Method method, RoutingContext ctx) {
    Parameter[] parameters = method.getParameters();
    Object[] args = new Object[parameters.length];

    for (int i = 0; i < parameters.length; i++) {
      Parameter parameter = parameters[i];
      Class<?> paramType = parameter.getType();

      if (paramType.equals(RoutingContext.class)) {
        args[i] = ctx;
      } else {
        // 这里可以扩展更多参数类型的处理，例如请求体对象、路径参数等
        args[i] = null;
      }
    }

    return args;
  }
}
