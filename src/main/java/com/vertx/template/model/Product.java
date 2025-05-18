package com.vertx.template.model;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 产品实体类
 */
public class Product {

  private String id;

  @NotBlank(message = "产品名称不能为空")
  @Size(min = 2, max = 50, message = "产品名称长度必须在2-50之间")
  private String name;

  @NotNull(message = "产品价格不能为空")
  @Min(value = 0, message = "产品价格必须大于等于0")
  private Double price;

  private String description;

  public Product() {
  }

  public Product(String id, String name, Double price, String description) {
    this.id = id;
    this.name = name;
    this.price = price;
    this.description = description;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public Double getPrice() {
    return price;
  }

  public void setPrice(Double price) {
    this.price = price;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }
}
