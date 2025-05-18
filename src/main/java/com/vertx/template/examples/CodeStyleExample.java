package com.vertx.template.examples;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 代码风格示例类，展示项目规范的应用
 */
public class CodeStyleExample {

  // 不可变常量使用大写+下划线
  private static final int MAX_RETRY_COUNT = 3;
  private static final String DEFAULT_TYPE = "default";

  // 成员变量默认使用final，如果需要修改则不使用
  private final Map<String, Object> immutableConfig;
  private Map<String, Object> mutableState;

  /**
   * 构造函数实践
   *
   * @param config 配置对象
   */
  public CodeStyleExample(final JsonObject config) {
    // 局部变量默认使用final
    final String configType = config.getString("type", DEFAULT_TYPE);

    // 使用不可变集合
    this.immutableConfig = Map.of(
        "type", configType,
        "created", System.currentTimeMillis());

    // 需要修改的集合不使用final
    this.mutableState = new ConcurrentHashMap<>();
  }

  /**
   * 同步方法示例
   *
   * @param id 对象ID
   * @return 处理结果
   */
  public String processData(final String id) {
    // 默认使用final的局部变量
    final StringBuilder result = new StringBuilder();

    // 方法链式调用
    result.append("Processing: ")
        .append(id)
        .append(" with type: ")
        .append(immutableConfig.get("type"));

    return result.toString();
  }

  /**
   * 异步方法示例，使用Future
   *
   * @param userId 用户ID
   * @return 处理结果的Future
   */
  public Future<List<String>> fetchUserItems(final String userId) {
    final Promise<List<String>> promise = Promise.promise();

    // 模拟异步操作
    // 可修改的集合不使用final
    List<String> items = new ArrayList<>();
    items.add("item1");
    items.add("item2");

    // 成功完成Promise
    promise.complete(items);

    return promise.future();
  }

  /**
   * Future组合示例
   *
   * @param userId 用户ID
   * @return 处理结果Future
   */
  public Future<Map<String, Object>> processUserData(final String userId) {
    return fetchUserItems(userId)
        .map(items -> {
          // 在map转换中使用final变量
          final Map<String, Object> result = new HashMap<>();
          result.put("userId", userId);
          result.put("items", items);
          result.put("count", items.size());
          return result;
        })
        .onSuccess(result -> {
          // 处理成功回调
          this.mutableState.put(userId, result);
        })
        .onFailure(err -> {
          // 异常处理
          System.err.println("处理失败: " + err.getMessage());
        });
  }
}
