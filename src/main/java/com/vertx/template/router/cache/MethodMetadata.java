package com.vertx.template.router.cache;

import com.vertx.template.router.annotation.HttpMethod;
import com.vertx.template.security.annotation.AuthType;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Optional;
import lombok.Builder;
import lombok.Getter;

/**
 * 方法元数据缓存类 缓存控制器方法的反射信息，避免运行时重复反射操作
 *
 * @author System
 * @since 1.0.0
 */
@Getter
public class MethodMetadata {

  /** 方法对象 */
  private final Method method;

  /** 方法所属的控制器类 */
  private final Class<?> controllerClass;

  /** HTTP方法类型 */
  private final HttpMethod httpMethod;

  /** 路由路径 */
  private final String path;

  /** 认证类型 */
  private final AuthType authType;

  /** 是否需要认证 */
  private final boolean requireAuth;

  /** 参数元数据列表 */
  private final List<ParameterMetadata> parameters;

  /** 是否有RequestBody参数 */
  private final boolean hasRequestBody;

  /** RequestBody参数的类型 */
  private final Class<?> requestBodyType;

  /** RequestBody是否需要校验 */
  private final boolean requestBodyNeedsValidation;

  // 构造函数
  public MethodMetadata(
      Method method,
      Class<?> controllerClass,
      HttpMethod httpMethod,
      String path,
      AuthType authType,
      boolean requireAuth,
      List<ParameterMetadata> parameters,
      boolean hasRequestBody,
      Class<?> requestBodyType,
      boolean requestBodyNeedsValidation) {
    this.method = method;
    this.controllerClass = controllerClass;
    this.httpMethod = httpMethod;
    this.path = path;
    this.authType = authType;
    this.requireAuth = requireAuth;
    this.parameters = parameters;
    this.hasRequestBody = hasRequestBody;
    this.requestBodyType = requestBodyType;
    this.requestBodyNeedsValidation = requestBodyNeedsValidation;
  }

  // Getter方法
  public Method getMethod() {
    return method;
  }

  public Class<?> getControllerClass() {
    return controllerClass;
  }

  public HttpMethod getHttpMethod() {
    return httpMethod;
  }

  public String getPath() {
    return path;
  }

  public AuthType getAuthType() {
    return authType;
  }

  public boolean isRequireAuth() {
    return requireAuth;
  }

  public List<ParameterMetadata> getParameters() {
    return parameters;
  }

  public boolean isHasRequestBody() {
    return hasRequestBody;
  }

  public Class<?> getRequestBodyType() {
    return requestBodyType;
  }

  public boolean isRequestBodyNeedsValidation() {
    return requestBodyNeedsValidation;
  }

  // Builder模式
  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private Method method;
    private Class<?> controllerClass;
    private HttpMethod httpMethod;
    private String path;
    private AuthType authType;
    private boolean requireAuth;
    private List<ParameterMetadata> parameters;
    private boolean hasRequestBody;
    private Class<?> requestBodyType;
    private boolean requestBodyNeedsValidation;

    public Builder method(Method method) {
      this.method = method;
      return this;
    }

    public Builder controllerClass(Class<?> controllerClass) {
      this.controllerClass = controllerClass;
      return this;
    }

    public Builder httpMethod(HttpMethod httpMethod) {
      this.httpMethod = httpMethod;
      return this;
    }

    public Builder path(String path) {
      this.path = path;
      return this;
    }

    public Builder authType(AuthType authType) {
      this.authType = authType;
      return this;
    }

    public Builder requireAuth(boolean requireAuth) {
      this.requireAuth = requireAuth;
      return this;
    }

    public Builder parameters(List<ParameterMetadata> parameters) {
      this.parameters = parameters;
      return this;
    }

    public Builder hasRequestBody(boolean hasRequestBody) {
      this.hasRequestBody = hasRequestBody;
      return this;
    }

    public Builder requestBodyType(Class<?> requestBodyType) {
      this.requestBodyType = requestBodyType;
      return this;
    }

    public Builder requestBodyNeedsValidation(boolean requestBodyNeedsValidation) {
      this.requestBodyNeedsValidation = requestBodyNeedsValidation;
      return this;
    }

    public MethodMetadata build() {
      return new MethodMetadata(
          method,
          controllerClass,
          httpMethod,
          path,
          authType,
          requireAuth,
          parameters,
          hasRequestBody,
          requestBodyType,
          requestBodyNeedsValidation);
    }
  }

  /** 参数元数据内部类 */
  @Getter
  public static class ParameterMetadata {

    /** 参数对象 */
    private final Parameter parameter;

    /** 参数类型 */
    private final Class<?> type;

    /** 参数名称 */
    private final String name;

    /** 参数注解类型 */
    private final ParameterType parameterType;

    /** 注解值（如@PathParam的value） */
    private final String annotationValue;

    /** 是否必需 */
    private final boolean required;

    /** 是否需要校验 */
    private final boolean needsValidation;

    // 构造函数
    public ParameterMetadata(
        Parameter parameter,
        Class<?> type,
        String name,
        ParameterType parameterType,
        String annotationValue,
        boolean required,
        boolean needsValidation) {
      this.parameter = parameter;
      this.type = type;
      this.name = name;
      this.parameterType = parameterType;
      this.annotationValue = annotationValue;
      this.required = required;
      this.needsValidation = needsValidation;
    }

    // Builder模式
    public static Builder builder() {
      return new Builder();
    }

    public static class Builder {
      private Parameter parameter;
      private Class<?> type;
      private String name;
      private ParameterType parameterType;
      private String annotationValue;
      private boolean required;
      private boolean needsValidation;

      public Builder parameter(Parameter parameter) {
        this.parameter = parameter;
        return this;
      }

      public Builder type(Class<?> type) {
        this.type = type;
        return this;
      }

      public Builder name(String name) {
        this.name = name;
        return this;
      }

      public Builder parameterType(ParameterType parameterType) {
        this.parameterType = parameterType;
        return this;
      }

      public Builder annotationValue(String annotationValue) {
        this.annotationValue = annotationValue;
        return this;
      }

      public Builder required(boolean required) {
        this.required = required;
        return this;
      }

      public Builder needsValidation(boolean needsValidation) {
        this.needsValidation = needsValidation;
        return this;
      }

      public ParameterMetadata build() {
        return new ParameterMetadata(
            parameter, type, name, parameterType, annotationValue, required, needsValidation);
      }
    }
  }

  /** 参数类型枚举 */
  public enum ParameterType {
    PATH_PARAM,
    QUERY_PARAM,
    HEADER_PARAM,
    REQUEST_BODY,
    CURRENT_USER,
    UNKNOWN
  }

  /**
   * 获取认证类型
   *
   * @return 认证类型，如果不需要认证则返回Optional.empty()
   */
  public Optional<AuthType> getAuthTypeOptional() {
    return requireAuth ? Optional.of(authType) : Optional.empty();
  }

  /**
   * 获取指定类型的参数元数据
   *
   * @param parameterType 参数类型
   * @return 参数元数据列表
   */
  public List<ParameterMetadata> getParametersByType(ParameterType parameterType) {
    return parameters.stream().filter(p -> p.getParameterType() == parameterType).toList();
  }

  /**
   * 检查是否有指定类型的参数
   *
   * @param parameterType 参数类型
   * @return 是否存在该类型参数
   */
  public boolean hasParameterType(ParameterType parameterType) {
    return parameters.stream().anyMatch(p -> p.getParameterType() == parameterType);
  }
}
