package com.vertx.template.model.entity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 用户实体类 */
@Data
@EqualsAndHashCode(callSuper = false)
public class User extends BaseEntity {
  private Long id;

  @NotBlank(message = "用户名不能为空") @Size(min = 3, max = 50, message = "用户名长度必须在3-50之间") private String username;

  @NotBlank(message = "密码不能为空") @Size(min = 6, max = 100, message = "密码长度必须在6-100之间") private String password;

  @Email(message = "邮箱格式不正确") private String email;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private boolean active;

  /**
   * 用于Row映射的工厂方法
   *
   * @param row 数据库行对象
   * @return User实体对象
   */
  public static User fromRow(io.vertx.sqlclient.Row row) {
    return BaseEntity.fromRow(row, User.class);
  }
}
