package com.vertx.template.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 用户数据传输对象 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDto {
  private String id;
  private String name;
}
