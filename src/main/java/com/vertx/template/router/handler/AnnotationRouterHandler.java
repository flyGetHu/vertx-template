package com.vertx.template.router.handler;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.vertx.template.exception.RateLimitException;
import com.vertx.template.exception.RouteRegistrationException;
import com.vertx.template.exception.ValidationException;
import com.vertx.template.middleware.auth.AuthenticationException;
import com.vertx.template.middleware.auth.AuthenticationManager;
import com.vertx.template.middleware.auth.annotation.AuthType;
import com.vertx.template.middleware.auth.annotation.RequireAuth;
import com.vertx.template.middleware.ratelimit.annotation.RateLimit;
import com.vertx.template.middleware.ratelimit.interceptor.RateLimitInterceptor;
import com.vertx.template.middleware.response.ResponseHandler;
import com.vertx.template.router.annotation.*;
import com.vertx.template.router.cache.MethodMetadata;
import com.vertx.template.router.cache.ReflectionCache;
import com.vertx.template.router.executor.RequestExecutor;
import com.vertx.template.router.resolver.ParameterResolver;
import com.vertx.template.router.scanner.RouteScanner;
import io.vertx.core.Handler;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import java.lang.reflect.Method;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 重构后的注解路由处理器，职责简化为路由协调和注册 @功能描述 协调各组件完成路由注册和请求处理 @职责范围 路由注册协调、请求分发、组件整合 */
@Singleton
public class AnnotationRouterHandler {

  private static final Logger logger = LoggerFactory.getLogger(AnnotationRouterHandler.class);

  private final Injector injector;
  private final ResponseHandler responseHandler;
  private final ReflectionCache reflectionCache;
  private final AuthenticationManager authenticationManager;
  private final RateLimitInterceptor rateLimitInterceptor;
  private final RouteScanner routeScanner;
  private final ParameterResolver parameterResolver;
  private final RequestExecutor requestExecutor;

  @Inject
  public AnnotationRouterHandler(
      Injector injector,
      ResponseHandler responseHandler,
      ReflectionCache reflectionCache,
      AuthenticationManager authenticationManager,
      RateLimitInterceptor rateLimitInterceptor,
      RouteScanner routeScanner,
      ParameterResolver parameterResolver,
      RequestExecutor requestExecutor) {
    this.injector = injector;
    this.responseHandler = responseHandler;
    this.reflectionCache = reflectionCache;
    this.authenticationManager = authenticationManager;
    this.rateLimitInterceptor = rateLimitInterceptor;
    this.routeScanner = routeScanner;
    this.parameterResolver = parameterResolver;
    this.requestExecutor = requestExecutor;
  }

  /**
   * 注册所有带有路由注解的控制器方法
   *
   * @param router 路由器
   * @throws RouteRegistrationException 路由注册失败时抛出
   */
  public void registerRoutes(Router router) {
    try {
      logger.info("开始扫描并注册路由");

      Set<Class<?>> controllerClasses = routeScanner.scanControllers();

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
        }
      }

      if (failureCount > 0) {
        String errorMsg = String.format("路由注册部分失败：成功 %d 个，失败 %d 个控制器", successCount, failureCount);
        logger.error(errorMsg);
        throw new RouteRegistrationException(errorMsg);
      }

      logger.info("基于注解的路由注册完成，共成功注册 {} 个控制器", successCount);
    } catch (RouteRegistrationException e) {
      throw e;
    } catch (Exception e) {
      String errorMsg = "路由注册过程中发生严重异常: " + e.getMessage();
      logger.error(errorMsg, e);
      throw new RouteRegistrationException(errorMsg, e);
    }
  }

  /** 注册单个控制器类中的所有路由方法 */
  private void registerController(Router router, Class<?> controllerClass) {
    try {
      Object controller = injector.getInstance(controllerClass);

      String basePath = "";
      if (controllerClass.isAnnotationPresent(RequestMapping.class)) {
        RequestMapping requestMapping = controllerClass.getAnnotation(RequestMapping.class);
        basePath = requestMapping.value();
      }

      int methodCount = 0;
      for (Method method : controllerClass.getMethods()) {
        if (processMethod(router, controller, method, basePath)) {
          reflectionCache.cacheMethod(method, controllerClass);
          methodCount++;
        }
      }

      logger.debug("控制器 [{}] 注册完成，共注册 {} 个路由方法", controllerClass.getSimpleName(), methodCount);

    } catch (Exception e) {
      throw new RouteRegistrationException(
          String.format("注册控制器 [%s] 时发生异常: %s", controllerClass.getSimpleName(), e.getMessage()),
          e);
    }
  }

  /** 处理控制器方法，注册对应的路由 */
  private boolean processMethod(Router router, Object controller, Method method, String basePath) {
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
        route = router.route(path);
      } else {
        route = router.route(path);
        for (HttpMethod httpMethod : mapping.method()) {
          route.method(convertHttpMethod(httpMethod));
        }
      }

      registerHandler(route, controller, method);
      logger.debug("注册通用路由: {}", path);
      return true;
    }

    return false;
  }

  /** 为路由注册处理器 */
  private void registerHandler(Route route, Object controller, Method method) {
    if (needsBodyHandler(method)) {
      route.handler(io.vertx.ext.web.handler.BodyHandler.create());
    }

    Handler<RoutingContext> handler = createHandler(controller, method);
    route.handler(handler);
  }

  /** 创建路由处理器 */
  private Handler<RoutingContext> createHandler(Object controller, Method method) {
    return responseHandler.handle(
        ctx -> {
          try {
            return executeRouteHandler(ctx, controller, method);
          } catch (Exception e) {
            logger.error("处理请求时发生异常", e);
            throw e;
          }
        });
  }

  /** 执行路由处理逻辑 */
  private Object executeRouteHandler(RoutingContext ctx, Object controller, Method method) {
    try {
      // 1. 执行认证检查
      performAuthentication(ctx, controller.getClass(), method);

      // 2. 执行限流检查
      performRateLimitCheck(ctx, controller.getClass(), method);

      // 3. 从缓存获取方法元数据
      final MethodMetadata metadata = reflectionCache.getMethodMetadata(method);

      // 4. 解析方法参数
      final Object[] args = parameterResolver.resolveArguments(metadata, method, ctx);

      // 5. 调用控制器方法并处理结果
      return requestExecutor.execute(controller, method, args);

    } catch (AuthenticationException | RateLimitException | ValidationException e) {
      // 认证、限流、参数校验异常需要重新抛出，交给全局异常处理器处理
      logger.debug("请求处理被中断: {}", e.getMessage());
      throw e;
    } catch (Exception e) {
      // 其他异常进行标准化处理后重新抛出
      logger.error("处理请求时发生异常", e);
      Exception normalizedEx = requestExecutor.normalizeException(e);
      if (normalizedEx instanceof RuntimeException) {
        throw (RuntimeException) normalizedEx;
      } else {
        throw new RuntimeException(normalizedEx);
      }
    }
  }

  /** 执行认证检查 */
  private void performAuthentication(RoutingContext ctx, Class<?> controllerClass, Method method) {
    RequireAuth methodAuth = method.getAnnotation(RequireAuth.class);
    RequireAuth classAuth = controllerClass.getAnnotation(RequireAuth.class);

    AuthType authType = AuthType.JWT;
    if (methodAuth != null) {
      authType = methodAuth.value();
    } else if (classAuth != null) {
      authType = classAuth.value();
    }

    if (authType != AuthType.NONE) {
      authenticationManager.authenticate(ctx, authType);
    }
  }

  /** 执行限流检查 */
  private void performRateLimitCheck(RoutingContext ctx, Class<?> controllerClass, Method method) {
    RateLimit methodRateLimit = method.getAnnotation(RateLimit.class);
    RateLimit classRateLimit = controllerClass.getAnnotation(RateLimit.class);

    if (methodRateLimit == null && classRateLimit == null) {
      return;
    }

    final MethodMetadata metadata = reflectionCache.getMethodMetadata(method);
    final Object[] args = parameterResolver.resolveArguments(metadata, method, ctx);

    rateLimitInterceptor.performRateLimitCheck(controllerClass, method, ctx, args);
  }

  // 工具方法
  private io.vertx.core.http.HttpMethod convertHttpMethod(HttpMethod method) {
    return io.vertx.core.http.HttpMethod.valueOf(method.name());
  }

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

  private boolean needsBodyHandler(Method method) {
    return method.isAnnotationPresent(PostMapping.class)
        || method.isAnnotationPresent(PutMapping.class)
        || hasRequestBodyParameter(method);
  }

  private boolean hasRequestBodyParameter(Method method) {
    return java.util.Arrays.stream(method.getParameters())
        .anyMatch(
            param ->
                java.util.Arrays.stream(param.getAnnotations())
                    .anyMatch(ann -> ann instanceof RequestBody));
  }
}
