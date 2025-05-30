package com.vertx.template.middleware.validation;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

/** 校验器工厂 */
public class ValidatorFactory {
  private static final Validator VALIDATOR;

  static {
    VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();
  }

  /** 获取校验器 */
  public static Validator getValidator() {
    return VALIDATOR;
  }
}
