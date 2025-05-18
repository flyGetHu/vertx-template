package com.vertx.template.model;

import lombok.Data;

@Data
public class ApiResponse<T> {
  private int code;
  private String msg;
  private T data;

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
}
