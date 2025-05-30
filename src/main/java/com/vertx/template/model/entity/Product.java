package com.vertx.template.model.entity;

import com.vertx.template.model.annotation.Id;
import com.vertx.template.model.annotation.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 产品实体类 - 演示使用数据库自增ID策略 */
@Data
@EqualsAndHashCode(callSuper = false)
@Table("products")
public class Product extends BaseEntity {

  /** 产品ID - 使用数据库自增策略 */
  @Id(generated = true)
  private Long id;

  @NotBlank(message = "产品名称不能为空") @Size(min = 2, max = 50, message = "产品名称长度必须在2-50之间") private String name;

  @NotNull(message = "产品价格不能为空") @Min(value = 0, message = "产品价格必须大于等于0") private Double price;

  private String description;

  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  private boolean active;

  /**
   * 用于Row映射的工厂方法
   *
   * @param row 数据库行对象
   * @return Product实体对象
   */
  public static Product fromRow(io.vertx.sqlclient.Row row) {
    return BaseEntity.fromRow(row, Product.class);
  }
}
