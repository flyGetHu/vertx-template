package com.vertx.template.exception;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 参数校验异常
 */
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

  public Map<String, List<String>> getValidationErrors() {
    return validationErrors;
  }
}
