package com.vertx.template.repository;

import java.util.List;

/**
 * 基础仓库接口，定义了通用的CRUD操作
 *
 * @param <T>
 *          实体类型
 * @param <ID>
 *          主键类型
 */
public interface BaseRepository<T, ID> {

  /**
   * 查找所有实体
   *
   * @return 包含所有实体的Future
   */
  List<T> findAll();

  /**
   * 根据ID查找实体
   *
   * @param id
   *          实体ID
   * @return 包含查询结果的Future
   */
  T findById(ID id);

  /**
   * 保存实体
   *
   * @param entity
   *          实体对象
   * @return 包含保存后实体的Future
   */
  T save(T entity);

  /**
   * 更新实体
   *
   * @param id
   *          实体ID
   * @param entity
   *          实体对象
   * @return 包含更新结果的Future
   */
  T update(ID id, T entity);

  /**
   * 删除实体
   *
   * @param id
   *          实体ID
   * @return 包含操作结果的Future
   */
  Boolean deleteById(ID id);
}
