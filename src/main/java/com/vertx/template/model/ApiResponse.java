package com.vertx.template.model;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

@Data
public class ApiResponse<T> {
  private int code;
  private String msg;
  private T data;
  private Map<String, Object> extra;

  public ApiResponse() {
    this.extra = new HashMap<>();
  }

  public static <T> ApiResponse<T> success(T data) {
    ApiResponse<T> response = new ApiResponse<>();
    response.code = 200;
    response.msg = "Success";
    response.data = data;
    return response;
  }

  public static ApiResponse<?> error(int code, String msg) {
    ApiResponse<?> response = new ApiResponse<>();
    response.code = code;
    response.msg = msg;
    return response;
  }

  /**
   * 添加额外信息
   *
   * @param key
   *          键
   * @param value
   *          值
   * @return 当前响应对象
   */
  public ApiResponse<T> setExtra(String key, Object value) {
    this.extra.put(key, value);
    return this;
  }
}
