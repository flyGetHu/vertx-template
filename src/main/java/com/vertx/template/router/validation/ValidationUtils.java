package com.vertx.template.router.validation;

import com.vertx.template.exception.ValidationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 校验工具类
 */
public class ValidationUtils {

  /**
   * 校验对象
   *
   * @param object
   *          待校验对象
   * @param <T>
   *          对象类型
   * @throws ValidationException
   *           校验失败时抛出
   */
  public static <T> void validate(T object) throws ValidationException {
    if (object == null) {
      throw new ValidationException("请求参数不能为空");
    }

    Validator validator = ValidatorFactory.getValidator();
    Set<ConstraintViolation<T>> violations = validator.validate(object);

    if (!violations.isEmpty()) {
      Map<String, List<String>> errors = new HashMap<>();

      for (ConstraintViolation<T> violation : violations) {
        String propertyPath = violation.getPropertyPath().toString();
        if (!errors.containsKey(propertyPath)) {
          errors.put(propertyPath, new ArrayList<>());
        }
        errors.get(propertyPath).add(violation.getMessage());
      }

      String errorMessage = String.format("参数校验失败: %s", violations.stream()
          .map(v -> String.format("%s %s", v.getPropertyPath(), v.getMessage())).collect(Collectors.joining(", ")));

      throw new ValidationException(errorMessage, errors);
    }
  }

  /**
   * 校验对象的指定属性
   *
   * @param object
   *          待校验对象
   * @param propertyName
   *          属性名
   * @param <T>
   *          对象类型
   * @throws ValidationException
   *           校验失败时抛出
   */
  public static <T> void validateProperty(T object, String propertyName) throws ValidationException {
    if (object == null) {
      throw new ValidationException("请求参数不能为空");
    }

    Validator validator = ValidatorFactory.getValidator();
    Set<ConstraintViolation<T>> violations = validator.validateProperty(object, propertyName);

    if (!violations.isEmpty()) {
      Map<String, List<String>> errors = new HashMap<>();
      List<String> errorMessages = violations.stream().map(ConstraintViolation::getMessage)
          .collect(Collectors.toList());
      errors.put(propertyName, errorMessages);

      String errorMessage = String.format("属性校验失败: %s %s", propertyName, String.join(", ", errorMessages));

      throw new ValidationException(errorMessage, errors);
    }
  }
}
