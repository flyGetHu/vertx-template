package com.vertx.template.controller;

import com.vertx.template.exception.BusinessException;
import com.vertx.template.model.dto.ProductDto;
import com.vertx.template.router.annotation.GetMapping;
import com.vertx.template.router.annotation.PathParam;
import com.vertx.template.router.annotation.PostMapping;
import com.vertx.template.router.annotation.QueryParam;
import com.vertx.template.router.annotation.RequestBody;
import com.vertx.template.router.annotation.RequestMapping;
import com.vertx.template.router.annotation.RestController;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;

/** 产品控制器，展示各种参数解析和校验功能 */
@RestController
@RequestMapping("/api/products")
@Singleton
public class ProductController {

  // 模拟数据存储
  private final Map<String, ProductDto> productStore = new ConcurrentHashMap<>();

  @Inject
  public ProductController() {
    // 初始化一些测试数据
    addProduct(new ProductDto("1", "示例产品", 99.9, "这是一个示例产品"));
    addProduct(new ProductDto("2", "高级产品", 199.9, "这是一个高级产品"));
  }

  /** 获取所有产品 */
  @GetMapping("")
  public List<ProductDto> getAllProducts(
      @QueryParam(value = "minPrice", required = false) Double minPrice,
      @QueryParam(value = "maxPrice", required = false) Double maxPrice) {

    List<ProductDto> products = new ArrayList<>(productStore.values());

    // 如果指定了价格范围，进行过滤
    if (minPrice != null || maxPrice != null) {
      return products.stream()
          .filter(p -> minPrice == null || p.getPrice() >= minPrice)
          .filter(p -> maxPrice == null || p.getPrice() <= maxPrice)
          .toList();
    }

    return products;
  }

  /** 根据ID获取产品 */
  @GetMapping("/:id")
  public ProductDto getProductById(@PathParam("id") String id) {
    ProductDto product = productStore.get(id);
    if (product == null) {
      throw new BusinessException(404, "产品不存在: " + id);
    }
    return product;
  }

  /** 创建新产品 使用@RequestBody和@Valid注解进行请求体校验 */
  @PostMapping("")
  public ProductDto createProduct(@Valid @RequestBody ProductDto product) {
    // 生成ID
    String id = UUID.randomUUID().toString();
    product.setId(id);

    // 保存产品
    productStore.put(id, product);
    return product;
  }

  /** 搜索产品 展示多个查询参数的使用 */
  @GetMapping("/search")
  public List<ProductDto> searchProducts(
      @QueryParam(value = "name", required = false) String name,
      @QueryParam(value = "minPrice", required = false) Double minPrice,
      @QueryParam(value = "maxPrice", required = false) Double maxPrice) {

    return productStore.values().stream()
        .filter(p -> name == null || p.getName().contains(name))
        .filter(p -> minPrice == null || p.getPrice() >= minPrice)
        .filter(p -> maxPrice == null || p.getPrice() <= maxPrice)
        .toList();
  }

  // 私有辅助方法
  private void addProduct(ProductDto product) {
    productStore.put(product.getId(), product);
  }
}
