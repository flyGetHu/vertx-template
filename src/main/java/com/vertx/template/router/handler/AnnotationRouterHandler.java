package com.vertx.template.router.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Injector;
import com.vertx.template.config.RouterConfig;
import com.vertx.template.exception.BusinessException;
import com.vertx.template.exception.RateLimitException;
import com.vertx.template.exception.RouteRegistrationException;
import com.vertx.template.exception.ValidationException;
import com.vertx.template.middleware.auth.AuthenticationException;
import com.vertx.template.middleware.auth.AuthenticationManager;
import com.vertx.template.middleware.auth.UserContext;
import com.vertx.template.middleware.auth.annotation.AuthType;
import com.vertx.template.middleware.auth.annotation.CurrentUser;
import com.vertx.template.middleware.auth.annotation.RequireAuth;
import com.vertx.template.middleware.ratelimit.interceptor.RateLimitInterceptor;
import com.vertx.template.middleware.response.ResponseHandler;
import com.vertx.template.middleware.validation.ValidationUtils;
import com.vertx.template.router.annotation.*;
import com.vertx.template.router.cache.MethodMetadata;
import com.vertx.template.router.cache.ReflectionCache;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import jakarta.validation.Valid;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 注解路由处理器，负责扫描并注册带有路由注解的控制器方法 */
@Singleton
public class AnnotationRouterHandler {
  private static final Logger logger = LoggerFactory.getLogger(AnnotationRouterHandler.class);
  private final Injector injector;
  private final ResponseHandler responseHandler;
  private final ObjectMapper objectMapper;
  private final AuthenticationManager authenticationManager;
  private final RouterConfig routerConfig;
  private final ReflectionCache reflectionCache;
  private final RateLimitInterceptor rateLimitInterceptor;

  @Inject
  public AnnotationRouterHandler(
      Injector injector,
      ResponseHandler responseHandler,
      AuthenticationManager authenticationManager,
      RouterConfig routerConfig,
      ReflectionCache reflectionCache,
      RateLimitInterceptor rateLimitInterceptor) {
    this.injector = injector;
    this.responseHandler = responseHandler;
    this.authenticationManager = authenticationManager;
    this.routerConfig = routerConfig;
    this.reflectionCache = reflectionCache;
    this.rateLimitInterceptor = rateLimitInterceptor;
    this.objectMapper = new ObjectMapper();
    this.objectMapper.findAndRegisterModules(); // 注册时间模块等
  }

  /**
   * 注册所有带有路由注解的控制器方法
   *
   * @param router 路由器
   * @throws RouteRegistrationException 路由注册失败时抛出
   */
  public void registerRoutes(Router router) {
    try {
      logger.info("开始扫描并注册路由，扫描包: {}", routerConfig.getBasePackage());

      Reflections reflections = new Reflections(routerConfig.getBasePackage());
      Set<Class<?>> controllerClasses = reflections.getTypesAnnotatedWith(RestController.class);

      if (controllerClasses.isEmpty()) {
        logger.warn("未找到任何带有@RestController注解的控制器类，请检查包路径配置");
        return;
      }

      int successCount = 0;
      int failureCount = 0;

      for (Class<?> controllerClass : controllerClasses) {
        try {
          registerController(router, controllerClass);
          successCount++;
        } catch (Exception e) {
          failureCount++;
          logger.error("注册控制器 [{}] 失败", controllerClass.getSimpleName(), e);
          // 继续注册其他控制器，不因单个失败而中断整个过程
        }
      }

      if (failureCount > 0) {
        String errorMsg = String.format("路由注册部分失败：成功 %d 个，失败 %d 个控制器", successCount, failureCount);
        logger.error(errorMsg);
        throw new RouteRegistrationException(errorMsg);
      }

      logger.info("基于注解的路由注册完成，共成功注册 {} 个控制器", successCount);
    } catch (RouteRegistrationException e) {
      throw e; // 重新抛出路由注册异常
    } catch (Exception e) {
      String errorMsg = "路由注册过程中发生严重异常: " + e.getMessage();
      logger.error(errorMsg, e);
      throw new RouteRegistrationException(errorMsg, e);
    }
  }

  /**
   * 注册单个控制器类中的所有路由方法
   *
   * @param router 路由器
   * @param controllerClass 控制器类
   * @throws RouteRegistrationException 控制器注册失败时抛出
   */
  private void registerController(Router router, Class<?> controllerClass) {
    try {
      // 获取控制器实例
      Object controller;
      try {
        controller = injector.getInstance(controllerClass);
      } catch (Exception e) {
        throw new RouteRegistrationException(
            String.format("无法创建控制器实例 [%s]: %s", controllerClass.getSimpleName(), e.getMessage()),
            e);
      }

      // 获取类级别的RequestMapping注解
      String basePath = "";
      if (controllerClass.isAnnotationPresent(RequestMapping.class)) {
        RequestMapping requestMapping = controllerClass.getAnnotation(RequestMapping.class);
        basePath = requestMapping.value();
      }

      // 处理所有方法
      int methodCount = 0;
      for (Method method : controllerClass.getMethods()) {
        try {
          if (processMethod(router, controller, method, basePath)) {
            // 缓存方法的反射信息
            reflectionCache.cacheMethod(method, controllerClass);
            methodCount++;
          }
        } catch (Exception e) {
          throw new RouteRegistrationException(
              String.format(
                  "注册控制器 [%s] 的方法 [%s] 失败: %s",
                  controllerClass.getSimpleName(), method.getName(), e.getMessage()),
              e);
        }
      }

      logger.debug("控制器 [{}] 注册完成，共注册 {} 个路由方法", controllerClass.getSimpleName(), methodCount);

    } catch (RouteRegistrationException e) {
      throw e; // 重新抛出路由注册异常
    } catch (Exception e) {
      throw new RouteRegistrationException(
          String.format("注册控制器 [%s] 时发生未知异常: %s", controllerClass.getSimpleName(), e.getMessage()),
          e);
    }
  }

  /**
   * 处理控制器方法，注册对应的路由
   *
   * @param router 路由器
   * @param controller 控制器实例
   * @param method 控制器方法
   * @param basePath 基础路径
   * @return 是否成功注册了路由
   */
  private boolean processMethod(Router router, Object controller, Method method, String basePath) {
    // 处理各种HTTP方法注解
    if (method.isAnnotationPresent(GetMapping.class)) {
      GetMapping mapping = method.getAnnotation(GetMapping.class);
      String path = combinePath(basePath, mapping.value());
      Route route = router.get(path);
      registerHandler(route, controller, method);
      logger.debug("注册GET路由: {}", path);
      return true;
    } else if (method.isAnnotationPresent(PostMapping.class)) {
      PostMapping mapping = method.getAnnotation(PostMapping.class);
      String path = combinePath(basePath, mapping.value());
      Route route = router.post(path);
      registerHandler(route, controller, method);
      logger.debug("注册POST路由: {}", path);
      return true;
    } else if (method.isAnnotationPresent(PutMapping.class)) {
      PutMapping mapping = method.getAnnotation(PutMapping.class);
      String path = combinePath(basePath, mapping.value());
      Route route = router.put(path);
      registerHandler(route, controller, method);
      logger.debug("注册PUT路由: {}", path);
      return true;
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
      return true;
    }

    return false; // 没有找到路由注解
  }

  /** 将自定义HttpMethod转换为Vert.x的HttpMethod */
  private io.vertx.core.http.HttpMethod convertHttpMethod(HttpMethod method) {
    return io.vertx.core.http.HttpMethod.valueOf(method.name());
  }

  /** 合并基础路径和方法路径 */
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

  /** 为路由注册处理器 */
  private void registerHandler(Route route, Object controller, Method method) {
    Handler<RoutingContext> handler = createHandler(controller, method);
    route.handler(handler);
  }

  /** 创建路由处理器 */
  private Handler<RoutingContext> createHandler(Object controller, Method method) {
    return responseHandler.handle(
        ctx -> {
          try {
            // 从缓存获取方法元数据，避免重复反射
            MethodMetadata metadata = reflectionCache.getMethodMetadata(method);

            // 检查认证类型
            AuthType authType =
                metadata != null ? metadata.getAuthType() : getAuthType(controller, method);
            if (metadata == null || metadata.isRequireAuth()) {
              authenticationManager.authenticate(ctx, authType);
            }

            // 使用缓存的元数据解析参数
            Object[] args =
                metadata != null
                    ? resolveMethodArgsFromCache(metadata, ctx)
                    : resolveMethodArgs(method, ctx);

            // 执行限流检查（在参数解析后，方法调用前）
            rateLimitInterceptor.performRateLimitCheck(controller.getClass(), method, ctx, args);

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
          } catch (RateLimitException e) {
            logger.debug("限流检查失败: {}", e.getMessage());
            throw e;
          } catch (Exception e) {
            logger.error("调用控制器方法时发生异常", e);
            throw switch (e) {
              case AuthenticationException authEx -> authEx;
              case RateLimitException rateLimitEx -> rateLimitEx;
              case Exception ex when ex.getCause() instanceof BusinessException ->
                  (BusinessException) ex.getCause();
              case Exception ex when ex.getCause() instanceof AuthenticationException ->
                  (AuthenticationException) ex.getCause();
              case Exception ex when ex.getCause() instanceof RateLimitException ->
                  (RateLimitException) ex.getCause();
              default -> new BusinessException(500, "Internal Server Error");
            };
          }
        });
  }

  /** 使用缓存的元数据解析方法参数 */
  private Object[] resolveMethodArgsFromCache(MethodMetadata metadata, RoutingContext ctx) {
    List<MethodMetadata.ParameterMetadata> parameters = metadata.getParameters();
    Object[] args = new Object[parameters.size()];

    for (int i = 0; i < parameters.size(); i++) {
      MethodMetadata.ParameterMetadata paramMetadata = parameters.get(i);
      args[i] = resolveParameterFromCache(paramMetadata, ctx);
    }

    return args;
  }

  /** 基于缓存元数据解析单个参数 */
  private Object resolveParameterFromCache(
      MethodMetadata.ParameterMetadata paramMetadata, RoutingContext ctx) {
    return switch (paramMetadata.getParameterType()) {
      case PATH_PARAM -> {
        String value = ctx.pathParam(paramMetadata.getName());

        // 检查参数长度限制
        if (value != null
            && routerConfig.isEnableParameterValidation()
            && value.length() > routerConfig.getMaxParameterLength()) {
          throw new ValidationException(
              String.format(
                  "路径参数 %s 长度超过限制，最大允许 %d 字符，实际 %d 字符",
                  paramMetadata.getName(), routerConfig.getMaxParameterLength(), value.length()));
        }

        yield convertValueSafely(value, paramMetadata.getType(), "路径参数 " + paramMetadata.getName());
      }

      case QUERY_PARAM -> {
        String value = ctx.request().getParam(paramMetadata.getName());

        if (value == null && paramMetadata.isRequired()) {
          throw new ValidationException(String.format("查询参数 %s 不能为空", paramMetadata.getName()));
        }

        // 检查参数长度限制
        if (value != null
            && routerConfig.isEnableParameterValidation()
            && value.length() > routerConfig.getMaxParameterLength()) {
          throw new ValidationException(
              String.format(
                  "查询参数 %s 长度超过限制，最大允许 %d 字符，实际 %d 字符",
                  paramMetadata.getName(), routerConfig.getMaxParameterLength(), value.length()));
        }

        yield convertValueSafely(value, paramMetadata.getType(), "查询参数 " + paramMetadata.getName());
      }

      case HEADER_PARAM -> {
        String value = ctx.request().getHeader(paramMetadata.getName());

        if (value == null && paramMetadata.isRequired()) {
          throw new ValidationException(String.format("请求头 %s 不能为空", paramMetadata.getName()));
        }

        // 检查参数长度限制
        if (value != null
            && routerConfig.isEnableParameterValidation()
            && value.length() > routerConfig.getMaxParameterLength()) {
          throw new ValidationException(
              String.format(
                  "请求头 %s 长度超过限制，最大允许 %d 字符，实际 %d 字符",
                  paramMetadata.getName(), routerConfig.getMaxParameterLength(), value.length()));
        }

        yield convertValueSafely(value, paramMetadata.getType(), "请求头 " + paramMetadata.getName());
      }

      case REQUEST_BODY -> {
        try {
          // 检查请求体大小限制
          if (ctx.body() != null && ctx.body().length() > routerConfig.getMaxRequestBodySize()) {
            throw new ValidationException(
                String.format(
                    "请求体大小超过限制，最大允许 %d 字节，实际 %d 字节",
                    routerConfig.getMaxRequestBodySize(), ctx.body().length()));
          }

          JsonObject jsonBody = ctx.body().asJsonObject();
          if (jsonBody == null || jsonBody.isEmpty()) {
            throw new ValidationException("请求体不能为空");
          }

          // 使用Jackson ObjectMapper进行转换
          Object body;
          if (paramMetadata.getType() == JsonObject.class) {
            body = jsonBody;
          } else {
            try {
              String jsonString = jsonBody.encode();
              body = objectMapper.readValue(jsonString, paramMetadata.getType());
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
              throw new ValidationException("请求体JSON格式错误: " + e.getMessage());
            } catch (Exception e) {
              throw new ValidationException("请求体转换失败: " + e.getMessage());
            }
          }

          if (body == null) {
            throw new ValidationException("请求体转换后为空");
          }

          // 检查是否需要校验
          if (paramMetadata.isNeedsValidation() && routerConfig.isEnableParameterValidation()) {
            try {
              ValidationUtils.validate(body);
            } catch (ValidationException e) {
              throw e;
            } catch (Exception e) {
              throw new ValidationException("参数校验失败: " + e.getMessage());
            }
          }

          yield body;
        } catch (DecodeException e) {
          throw new ValidationException("请求体JSON解析失败: " + e.getMessage());
        } catch (ValidationException e) {
          throw e;
        } catch (Exception e) {
          throw new ValidationException("处理请求体时发生异常: " + e.getMessage());
        }
      }

      case CURRENT_USER -> {
        yield ctx.get("currentUser");
      }

      case UNKNOWN -> {
        if (paramMetadata.getType().equals(RoutingContext.class)) {
          yield ctx;
        } else {
          yield null;
        }
      }
    };
  }

  /** 获取认证类型（默认所有接口都需要JWT认证） */
  private AuthType getAuthType(Object controller, Method method) {
    // 先检查方法级别的注解
    RequireAuth methodAuth = method.getAnnotation(RequireAuth.class);
    if (methodAuth != null) {
      return methodAuth.value();
    }

    // 再检查类级别的注解
    RequireAuth classAuth = controller.getClass().getAnnotation(RequireAuth.class);
    if (classAuth != null) {
      return classAuth.value();
    }

    // 默认使用JWT认证
    return AuthType.JWT;
  }

  /** 解析方法参数 */
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

  /** 解析带注解的参数 */
  private Object resolveAnnotatedParam(
      Parameter parameter, Annotation[] annotations, RoutingContext ctx) {
    Class<?> paramType = parameter.getType();
    Object result = null;

    // 先找到主要参数注解（PathParam、QueryParam等）
    for (Annotation annotation : annotations) {
      if (annotation instanceof PathParam
          || annotation instanceof QueryParam
          || annotation instanceof RequestBody
          || annotation instanceof HeaderParam
          || annotation instanceof CurrentUser) {

        result =
            switch (annotation) {
              case PathParam pathParam -> {
                String name = pathParam.value().isEmpty() ? parameter.getName() : pathParam.value();
                String value = ctx.pathParam(name);

                // 检查参数长度限制
                if (value != null
                    && routerConfig.isEnableParameterValidation()
                    && value.length() > routerConfig.getMaxParameterLength()) {
                  throw new ValidationException(
                      String.format(
                          "路径参数 %s 长度超过限制，最大允许 %d 字符，实际 %d 字符",
                          name, routerConfig.getMaxParameterLength(), value.length()));
                }

                yield convertValueSafely(value, paramType, "路径参数 " + name);
              }

              case QueryParam queryParam -> {
                String name =
                    queryParam.value().isEmpty() ? parameter.getName() : queryParam.value();
                String value = ctx.request().getParam(name);

                if (value == null && queryParam.required()) {
                  throw new ValidationException(String.format("查询参数 %s 不能为空", name));
                }

                // 检查参数长度限制
                if (value != null
                    && routerConfig.isEnableParameterValidation()
                    && value.length() > routerConfig.getMaxParameterLength()) {
                  throw new ValidationException(
                      String.format(
                          "查询参数 %s 长度超过限制，最大允许 %d 字符，实际 %d 字符",
                          name, routerConfig.getMaxParameterLength(), value.length()));
                }

                yield convertValueSafely(value, paramType, "查询参数 " + name);
              }

              case RequestBody ignored -> {
                try {
                  // 检查请求体大小限制
                  if (ctx.body() != null
                      && ctx.body().length() > routerConfig.getMaxRequestBodySize()) {
                    throw new ValidationException(
                        String.format(
                            "请求体大小超过限制，最大允许 %d 字节，实际 %d 字节",
                            routerConfig.getMaxRequestBodySize(), ctx.body().length()));
                  }

                  JsonObject jsonBody = ctx.body().asJsonObject();
                  if (jsonBody == null || jsonBody.isEmpty()) {
                    throw new ValidationException("请求体不能为空");
                  }

                  // 使用Jackson ObjectMapper进行转换，而不是直接使用mapTo
                  Object body;
                  if (paramType == JsonObject.class) {
                    body = jsonBody;
                  } else {
                    try {
                      // 先转换为JSON字符串，再使用Jackson反序列化
                      String jsonString = jsonBody.encode();
                      body = objectMapper.readValue(jsonString, paramType);
                    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                      throw new ValidationException("请求体JSON格式错误: " + e.getMessage());
                    } catch (Exception e) {
                      throw new ValidationException("请求体转换失败: " + e.getMessage());
                    }
                  }

                  if (body == null) {
                    throw new ValidationException("请求体转换后为空");
                  }

                  // 只在RequestBody注解上判断是否需要校验
                  boolean needValidation =
                      Arrays.stream(annotations).anyMatch(a -> a instanceof Valid);

                  if (needValidation && routerConfig.isEnableParameterValidation()) {
                    try {
                      ValidationUtils.validate(body);
                    } catch (ValidationException e) {
                      throw e; // 重新抛出校验异常
                    } catch (Exception e) {
                      throw new ValidationException("参数校验失败: " + e.getMessage());
                    }
                  }

                  yield body;
                } catch (DecodeException e) {
                  throw new ValidationException("请求体JSON解析失败: " + e.getMessage());
                } catch (ValidationException e) {
                  throw e; // 重新抛出校验异常
                } catch (Exception e) {
                  throw new ValidationException("处理请求体时发生异常: " + e.getMessage());
                }
              }

              case HeaderParam headerParam -> {
                String name =
                    headerParam.value().isEmpty() ? parameter.getName() : headerParam.value();
                String value = ctx.request().getHeader(name);

                if (value == null && headerParam.required()) {
                  throw new ValidationException(String.format("请求头 %s 不能为空", name));
                }

                // 检查参数长度限制
                if (value != null
                    && routerConfig.isEnableParameterValidation()
                    && value.length() > routerConfig.getMaxParameterLength()) {
                  throw new ValidationException(
                      String.format(
                          "请求头 %s 长度超过限制，最大允许 %d 字符，实际 %d 字符",
                          name, routerConfig.getMaxParameterLength(), value.length()));
                }

                yield convertValueSafely(value, paramType, "请求头 " + name);
              }

              case CurrentUser ignored -> {
                UserContext userContext = AuthenticationManager.getCurrentUser(ctx);
                if (userContext == null) {
                  throw new ValidationException("当前用户上下文不存在，请确保已通过认证");
                }
                yield userContext;
              }

              default -> null;
            };

        break; // 找到主参数注解后跳出
      }
    }

    return result;
  }

  /** 安全转换字符串值为指定类型，提供详细的错误信息 */
  private Object convertValueSafely(String value, Class<?> targetType, String parameterName) {
    if (value == null) {
      return null;
    }

    try {
      return switch (targetType) {
        case Class<?> clazz when clazz == String.class -> value;
        case Class<?> clazz when clazz == Integer.class || clazz == int.class ->
            parseInteger(value, parameterName);
        case Class<?> clazz when clazz == Long.class || clazz == long.class ->
            parseLong(value, parameterName);
        case Class<?> clazz when clazz == Double.class || clazz == double.class ->
            parseDouble(value, parameterName);
        case Class<?> clazz when clazz == Boolean.class || clazz == boolean.class ->
            Boolean.parseBoolean(value);
        default -> parseComplexType(value, targetType, parameterName);
      };
    } catch (ValidationException e) {
      throw e; // 重新抛出校验异常
    } catch (Exception e) {
      throw new ValidationException(
          String.format(
              "%s 类型转换失败: 无法将值 '%s' 转换为 %s 类型", parameterName, value, targetType.getSimpleName()));
    }
  }

  private Integer parseInteger(String value, String parameterName) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      throw new ValidationException(
          String.format("%s 格式错误: 无法将值 '%s' 转换为整数", parameterName, value));
    }
  }

  private Long parseLong(String value, String parameterName) {
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      throw new ValidationException(
          String.format("%s 格式错误: 无法将值 '%s' 转换为长整数", parameterName, value));
    }
  }

  private Double parseDouble(String value, String parameterName) {
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException e) {
      throw new ValidationException(
          String.format("%s 格式错误: 无法将值 '%s' 转换为浮点数", parameterName, value));
    }
  }

  private Object parseComplexType(String value, Class<?> targetType, String parameterName) {
    try {
      return new JsonObject().put("value", value).mapTo(Map.class).get("value");
    } catch (Exception e) {
      throw new ValidationException(
          String.format("%s 类型转换失败: 不支持的参数类型 %s", parameterName, targetType.getName()));
    }
  }
}
