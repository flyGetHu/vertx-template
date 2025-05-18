package com.vertx.template.router.handler;

import com.google.inject.Injector;
import com.vertx.template.exception.BusinessException;
import com.vertx.template.exception.ValidationException;
import com.vertx.template.handler.ResponseHandler;
import com.vertx.template.router.annotation.*;
import com.vertx.template.router.validation.ValidationUtils;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.validation.Valid;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Set;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

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
    } else if (method.isAnnotationPresent(PutMapping.class)) {
      PutMapping mapping = method.getAnnotation(PutMapping.class);
      String path = combinePath(basePath, mapping.value());
      Route route = router.put(path);
      registerHandler(route, controller, method);
      logger.debug("注册PUT路由: {}", path);
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
        // 判断方法参数类型并注入值
        Object[] args = resolveMethodArgs(method, ctx);

        // 调用控制器方法
        Object result = method.invoke(controller, args);

        // 处理Future结果
        if (result instanceof Future) {
          return Future.await((Future<?>) result);
        }

        return result;
      } catch (ValidationException e) {
        logger.debug("参数校验失败: {}", e.getMessage());
        throw e;
      } catch (Exception e) {
        logger.error("调用控制器方法时发生异常", e);
        if (e.getCause() instanceof BusinessException) {
          throw (BusinessException) e.getCause();
        }
        throw new BusinessException(500, "Internal Server Error");
      }
    });
  }

  /**
   * 解析方法参数
   */
  private Object[] resolveMethodArgs(Method method, RoutingContext ctx) {
    Parameter[] parameters = method.getParameters();
    Object[] args = new Object[parameters.length];
    Annotation[][] paramAnnotations = method.getParameterAnnotations();

    for (int i = 0; i < parameters.length; i++) {
      Parameter parameter = parameters[i];
      Class<?> paramType = parameter.getType();
      Annotation[] annotations = paramAnnotations[i];

      // 检查参数是否有注解
      if (annotations.length > 0) {
        args[i] = resolveAnnotatedParam(parameter, annotations, ctx);
      } else if (paramType.equals(RoutingContext.class)) {
        // 处理RoutingContext类型参数
        args[i] = ctx;
      } else {
        // 对于没有注解的非RoutingContext参数，设为null
        args[i] = null;
      }
    }

    return args;
  }

  /**
   * 解析带注解的参数
   */
  private Object resolveAnnotatedParam(Parameter parameter, Annotation[] annotations, RoutingContext ctx) {
    Class<?> paramType = parameter.getType();
    Object result = null;

    // 先找到主要参数注解（PathParam、QueryParam等）
    for (Annotation annotation : annotations) {
      if (annotation instanceof PathParam ||
          annotation instanceof QueryParam ||
          annotation instanceof RequestBody ||
          annotation instanceof HeaderParam) {

        result = switch (annotation) {
          case PathParam pathParam -> {
            String name = pathParam.value().isEmpty() ? parameter.getName() : pathParam.value();
            String value = ctx.pathParam(name);
            yield convertValue(value, paramType);
          }

          case QueryParam queryParam -> {
            String name = queryParam.value().isEmpty() ? parameter.getName() : queryParam.value();
            String value = ctx.request().getParam(name);

            if (value == null && queryParam.required()) {
              throw new ValidationException(String.format("查询参数 %s 不能为空", name));
            }

            yield convertValue(value, paramType);
          }

          case RequestBody ignored -> {
            try {
              Object body = ctx.body().asJsonObject().mapTo(paramType);
              if (body == null) {
                throw new ValidationException("请求体不能为空");
              }

              // 只在RequestBody注解上判断是否需要校验
              boolean needValidation = Arrays.stream(annotations)
                  .anyMatch(a -> a instanceof Valid);

              if (needValidation) {
                ValidationUtils.validate(body);
              }

              yield body;
            } catch (DecodeException e) {
              throw new ValidationException("请求体解析失败: " + e.getMessage());
            }
          }

          case HeaderParam headerParam -> {
            String name = headerParam.value().isEmpty() ? parameter.getName() : headerParam.value();
            String value = ctx.request().getHeader(name);

            if (value == null && headerParam.required()) {
              throw new ValidationException(String.format("请求头 %s 不能为空", name));
            }

            yield convertValue(value, paramType);
          }

          default -> null;
        };

        break; // 找到主参数注解后跳出
      }
    }

    return result;
  }

  /**
   * 转换字符串值为指定类型
   */
  private Object convertValue(String value, Class<?> targetType) {
    if (value == null) {
      return null;
    }

    return switch (targetType) {
      case Class<?> clazz when clazz == String.class -> value;
      case Class<?> clazz when clazz == Integer.class || clazz == int.class -> parseInteger(value);
      case Class<?> clazz when clazz == Long.class || clazz == long.class -> parseLong(value);
      case Class<?> clazz when clazz == Double.class || clazz == double.class -> parseDouble(value);
      case Class<?> clazz when clazz == Boolean.class || clazz == boolean.class -> Boolean.parseBoolean(value);
      default -> parseComplexType(value, targetType);
    };
  }

  private Integer parseInteger(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new ValidationException(String.format("无法将值 '%s' 转换为整数", value));
    }
  }

  private Long parseLong(String value) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      throw new ValidationException(String.format("无法将值 '%s' 转换为长整数", value));
    }
  }

  private Double parseDouble(String value) {
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      throw new ValidationException(String.format("无法将值 '%s' 转换为浮点数", value));
    }
  }

  private Object parseComplexType(String value, Class<?> targetType) {
    try {
      return new JsonObject().put("value", value).mapTo(Map.class).get("value");
    } catch (Exception e) {
      throw new ValidationException(String.format("不支持的参数类型转换: %s", targetType.getName()));
    }
  }
}
