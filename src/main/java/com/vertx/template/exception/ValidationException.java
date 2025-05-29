package com.vertx.template.exception;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;

/** 参数校验异常 */
@Getter
public class ValidationException extends BusinessException {
  private final Map<String, List<String>> validationErrors;

  public ValidationException(String message, Map<String, List<String>> validationErrors) {
    super(400, message);
    this.validationErrors = validationErrors;
  }

  public ValidationException(String message) {
    super(400, message);
    this.validationErrors = new HashMap<>();
  }

  // 手动添加getter方法以确保编译通过
  public Map<String, List<String>> getValidationErrors() {
    return validationErrors;
  }
}
