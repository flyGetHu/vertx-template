package com.vertx.template.router.cache;

import com.vertx.template.middleware.auth.annotation.AuthType;
import com.vertx.template.middleware.auth.annotation.CurrentUser;
import com.vertx.template.middleware.auth.annotation.RequireAuth;
import com.vertx.template.router.annotation.*;
import jakarta.validation.Valid;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 反射信息缓存管理器 在应用启动时缓存所有控制器方法的反射信息，运行时直接查表获取，避免重复反射操作
 *
 * @author System
 * @since 1.0.0
 */
@Singleton
public class ReflectionCache {

  private static final Logger logger = LoggerFactory.getLogger(ReflectionCache.class);

  /** 方法元数据缓存，key为Method对象，value为MethodMetadata */
  private final Map<Method, MethodMetadata> methodCache = new ConcurrentHashMap<>();

  /** 控制器类缓存，key为类名，value为Class对象 */
  private final Map<String, Class<?>> controllerCache = new ConcurrentHashMap<>();

  /** 缓存统计信息 */
  private volatile int cachedMethodCount = 0;

  private volatile int cachedControllerCount = 0;

  /**
   * 缓存控制器方法的反射信息
   *
   * @param method 控制器方法
   * @param controllerClass 控制器类
   */
  public void cacheMethod(Method method, Class<?> controllerClass) {
    try {
      MethodMetadata metadata = buildMethodMetadata(method, controllerClass);
      methodCache.put(method, metadata);
      controllerCache.put(controllerClass.getName(), controllerClass);
      cachedMethodCount++;

      logger.debug("缓存方法反射信息: {}.{}", controllerClass.getSimpleName(), method.getName());
    } catch (Exception e) {
      logger.error("缓存方法反射信息失败: {}.{}", controllerClass.getSimpleName(), method.getName(), e);
    }
  }

  /**
   * 获取方法的缓存元数据
   *
   * @param method 方法对象
   * @return 方法元数据，如果未缓存则返回null
   */
  public MethodMetadata getMethodMetadata(Method method) {
    return methodCache.get(method);
  }

  /**
   * 获取控制器类
   *
   * @param className 类名
   * @return 控制器类，如果未缓存则返回null
   */
  public Class<?> getControllerClass(String className) {
    return controllerCache.get(className);
  }

  /**
   * 检查方法是否已缓存
   *
   * @param method 方法对象
   * @return 是否已缓存
   */
  public boolean isCached(Method method) {
    return methodCache.containsKey(method);
  }

  /**
   * 获取缓存统计信息
   *
   * @return 缓存统计信息
   */
  public CacheStats getCacheStats() {
    return CacheStats.builder()
        .cachedMethodCount(cachedMethodCount)
        .cachedControllerCount(controllerCache.size())
        .cacheHitRate(calculateHitRate())
        .build();
  }

  /** 清空缓存 */
  public void clearCache() {
    methodCache.clear();
    controllerCache.clear();
    cachedMethodCount = 0;
    cachedControllerCount = 0;
    logger.info("反射缓存已清空");
  }

  /** 构建方法元数据 */
  private MethodMetadata buildMethodMetadata(Method method, Class<?> controllerClass) {
    // 获取HTTP方法和路径
    HttpMethodInfo httpMethodInfo = extractHttpMethodInfo(method);

    // 获取认证信息
    AuthInfo authInfo = extractAuthInfo(method, controllerClass);

    // 构建参数元数据
    List<MethodMetadata.ParameterMetadata> parameterMetadataList = buildParameterMetadata(method);

    // 检查RequestBody信息
    RequestBodyInfo requestBodyInfo = extractRequestBodyInfo(method);

    return MethodMetadata.builder()
        .method(method)
        .controllerClass(controllerClass)
        .httpMethod(httpMethodInfo.httpMethod)
        .path(httpMethodInfo.path)
        .authType(authInfo.authType)
        .requireAuth(authInfo.requireAuth)
        .parameters(parameterMetadataList)
        .hasRequestBody(requestBodyInfo.hasRequestBody)
        .requestBodyType(requestBodyInfo.requestBodyType)
        .requestBodyNeedsValidation(requestBodyInfo.needsValidation)
        .build();
  }

  /** 提取HTTP方法信息 */
  private HttpMethodInfo extractHttpMethodInfo(Method method) {
    if (method.isAnnotationPresent(GetMapping.class)) {
      GetMapping annotation = method.getAnnotation(GetMapping.class);
      return new HttpMethodInfo(HttpMethod.GET, annotation.value());
    } else if (method.isAnnotationPresent(PostMapping.class)) {
      PostMapping annotation = method.getAnnotation(PostMapping.class);
      return new HttpMethodInfo(HttpMethod.POST, annotation.value());
    } else if (method.isAnnotationPresent(PutMapping.class)) {
      PutMapping annotation = method.getAnnotation(PutMapping.class);
      return new HttpMethodInfo(HttpMethod.PUT, annotation.value());
    } else if (method.isAnnotationPresent(DeleteMapping.class)) {
      DeleteMapping annotation = method.getAnnotation(DeleteMapping.class);
      return new HttpMethodInfo(HttpMethod.DELETE, annotation.value());
    } else if (method.isAnnotationPresent(RequestMapping.class)) {
      RequestMapping annotation = method.getAnnotation(RequestMapping.class);
      HttpMethod httpMethod =
          annotation.method().length > 0 ? annotation.method()[0] : HttpMethod.GET;
      return new HttpMethodInfo(httpMethod, annotation.value());
    }
    return new HttpMethodInfo(HttpMethod.GET, "");
  }

  /** 提取认证信息 */
  private AuthInfo extractAuthInfo(Method method, Class<?> controllerClass) {
    RequireAuth methodAuth = method.getAnnotation(RequireAuth.class);
    RequireAuth classAuth = controllerClass.getAnnotation(RequireAuth.class);

    RequireAuth effectiveAuth = methodAuth != null ? methodAuth : classAuth;

    if (effectiveAuth != null) {
      return new AuthInfo(true, effectiveAuth.value());
    }
    return new AuthInfo(false, AuthType.NONE);
  }

  /** 构建参数元数据列表 */
  private List<MethodMetadata.ParameterMetadata> buildParameterMetadata(Method method) {
    Parameter[] parameters = method.getParameters();
    List<MethodMetadata.ParameterMetadata> metadataList = new ArrayList<>();

    for (Parameter parameter : parameters) {
      MethodMetadata.ParameterMetadata paramMetadata = buildSingleParameterMetadata(parameter);
      metadataList.add(paramMetadata);
    }

    return metadataList;
  }

  /** 构建单个参数的元数据 */
  private MethodMetadata.ParameterMetadata buildSingleParameterMetadata(Parameter parameter) {
    Annotation[] annotations = parameter.getAnnotations();

    for (Annotation annotation : annotations) {
      switch (annotation) {
        case PathParam pathParam -> {
          String name = pathParam.value().isEmpty() ? parameter.getName() : pathParam.value();
          return MethodMetadata.ParameterMetadata.builder()
              .parameter(parameter)
              .type(parameter.getType())
              .name(name)
              .parameterType(MethodMetadata.ParameterType.PATH_PARAM)
              .annotationValue(pathParam.value())
              .required(true) // PathParam总是必需的
              .needsValidation(false)
              .build();
        }
        case QueryParam queryParam -> {
          String name = queryParam.value().isEmpty() ? parameter.getName() : queryParam.value();
          return MethodMetadata.ParameterMetadata.builder()
              .parameter(parameter)
              .type(parameter.getType())
              .name(name)
              .parameterType(MethodMetadata.ParameterType.QUERY_PARAM)
              .annotationValue(queryParam.value())
              .required(queryParam.required())
              .needsValidation(false)
              .build();
        }
        case HeaderParam headerParam -> {
          String name = headerParam.value().isEmpty() ? parameter.getName() : headerParam.value();
          return MethodMetadata.ParameterMetadata.builder()
              .parameter(parameter)
              .type(parameter.getType())
              .name(name)
              .parameterType(MethodMetadata.ParameterType.HEADER_PARAM)
              .annotationValue(headerParam.value())
              .required(headerParam.required())
              .needsValidation(false)
              .build();
        }
        case RequestBody ignored -> {
          boolean needsValidation = Arrays.stream(annotations).anyMatch(a -> a instanceof Valid);
          return MethodMetadata.ParameterMetadata.builder()
              .parameter(parameter)
              .type(parameter.getType())
              .name("requestBody")
              .parameterType(MethodMetadata.ParameterType.REQUEST_BODY)
              .annotationValue("")
              .required(true)
              .needsValidation(needsValidation)
              .build();
        }
        case CurrentUser ignored -> {
          return MethodMetadata.ParameterMetadata.builder()
              .parameter(parameter)
              .type(parameter.getType())
              .name("currentUser")
              .parameterType(MethodMetadata.ParameterType.CURRENT_USER)
              .annotationValue("")
              .required(true)
              .needsValidation(false)
              .build();
        }
        default -> {
          // 继续检查其他注解
        }
      }
    }

    // 没有找到已知注解，标记为UNKNOWN
    return MethodMetadata.ParameterMetadata.builder()
        .parameter(parameter)
        .type(parameter.getType())
        .name(parameter.getName())
        .parameterType(MethodMetadata.ParameterType.UNKNOWN)
        .annotationValue("")
        .required(false)
        .needsValidation(false)
        .build();
  }

  /** 提取RequestBody信息 */
  private RequestBodyInfo extractRequestBodyInfo(Method method) {
    Parameter[] parameters = method.getParameters();

    for (Parameter parameter : parameters) {
      if (parameter.isAnnotationPresent(RequestBody.class)) {
        boolean needsValidation =
            Arrays.stream(parameter.getAnnotations()).anyMatch(a -> a instanceof Valid);
        return new RequestBodyInfo(true, parameter.getType(), needsValidation);
      }
    }

    return new RequestBodyInfo(false, null, false);
  }

  /** 计算缓存命中率（简化实现） */
  private double calculateHitRate() {
    // 这里可以实现更复杂的命中率统计
    return cachedMethodCount > 0 ? 1.0 : 0.0;
  }

  // 内部数据类
  private record HttpMethodInfo(HttpMethod httpMethod, String path) {}

  private record AuthInfo(boolean requireAuth, AuthType authType) {}

  private record RequestBodyInfo(
      boolean hasRequestBody, Class<?> requestBodyType, boolean needsValidation) {}

  /** 缓存统计信息 */
  public static class CacheStats {
    private final int cachedMethodCount;
    private final int cachedControllerCount;
    private final double cacheHitRate;

    private CacheStats(int cachedMethodCount, int cachedControllerCount, double cacheHitRate) {
      this.cachedMethodCount = cachedMethodCount;
      this.cachedControllerCount = cachedControllerCount;
      this.cacheHitRate = cacheHitRate;
    }

    public static Builder builder() {
      return new Builder();
    }

    public int getCachedMethodCount() {
      return cachedMethodCount;
    }

    public int getCachedControllerCount() {
      return cachedControllerCount;
    }

    public double getCacheHitRate() {
      return cacheHitRate;
    }

    public static class Builder {
      private int cachedMethodCount;
      private int cachedControllerCount;
      private double cacheHitRate;

      public Builder cachedMethodCount(int count) {
        this.cachedMethodCount = count;
        return this;
      }

      public Builder cachedControllerCount(int count) {
        this.cachedControllerCount = count;
        return this;
      }

      public Builder cacheHitRate(double rate) {
        this.cacheHitRate = rate;
        return this;
      }

      public CacheStats build() {
        return new CacheStats(cachedMethodCount, cachedControllerCount, cacheHitRate);
      }
    }
  }
}
