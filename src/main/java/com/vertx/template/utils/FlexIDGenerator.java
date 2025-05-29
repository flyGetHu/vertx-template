package com.vertx.template.utils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * FlexID 算法实现（基于 MyBatis-Flex 的 FlexIDKeyGenerator 改进）
 *
 * <p>特点： 1、保证 ID 生成的顺序为时间顺序，越往后生成的 ID 值越大 2、运行时，单台机器并发量在每秒钟 10w 以内 3、运行时，无视时间回拨 4、最大支持 99 台机器
 * 5、够用大概 300 年左右的时间
 *
 * <p>缺点： 1、每台机器允许最大的并发量为 10w/s 2、出现时间回拨，重启机器时，在时间回拨未恢复的情况下，可能出现 ID 重复
 *
 * <p>ID组成：时间（7+）| 毫秒内的时间自增 （00~99：2）| 机器ID（00 ~ 99：2）| 随机数（00~99：2）用于分库分表时，通过 ID 取模，保证分布均衡
 *
 * @author template
 * @since 1.0.0
 */
public class FlexIDGenerator {

  /** 初始时间戳 (2023-04-02 12:01:00) */
  private static final long INITIAL_TIMESTAMP = 1680411660000L;

  /** 最大时钟序列 */
  private static final long MAX_CLOCK_SEQ = 99;

  /** 最后一次生成 ID 的时间 */
  private long lastTimeMillis = 0;

  /** 时间序列 */
  private long clockSeq = 0;

  /** 机器 ID */
  private final long workId;

  /** 单例实例 */
  private static volatile FlexIDGenerator instance;

  /**
   * 私有构造函数
   *
   * @param workId 机器ID (0-99)
   */
  private FlexIDGenerator(long workId) {
    if (workId < 0 || workId > 99) {
      throw new IllegalArgumentException("机器ID必须在0-99之间");
    }
    this.workId = workId;
  }

  /**
   * 获取单例实例
   *
   * @return FlexIDGenerator实例
   */
  public static FlexIDGenerator getInstance() {
    if (instance == null) {
      synchronized (FlexIDGenerator.class) {
        if (instance == null) {
          // 默认机器ID为1，实际使用时可以通过配置文件或环境变量设置
          long workId = getWorkIdFromConfig();
          instance = new FlexIDGenerator(workId);
        }
      }
    }
    return instance;
  }

  /**
   * 从配置中获取机器ID 可以从系统属性、环境变量或配置文件中获取
   *
   * @return 机器ID
   */
  private static long getWorkIdFromConfig() {
    // 优先从系统属性获取
    String workIdStr = System.getProperty("flex.work.id");
    if (workIdStr != null) {
      try {
        long workId = Long.parseLong(workIdStr);
        if (workId >= 0 && workId <= 99) {
          return workId;
        }
      } catch (NumberFormatException e) {
        // 忽略解析错误，使用默认值
      }
    }

    // 从环境变量获取
    workIdStr = System.getenv("FLEX_WORK_ID");
    if (workIdStr != null) {
      try {
        long workId = Long.parseLong(workIdStr);
        if (workId >= 0 && workId <= 99) {
          return workId;
        }
      } catch (NumberFormatException e) {
        // 忽略解析错误，使用默认值
      }
    }

    // 默认机器ID为1
    return 1L;
  }

  /**
   * 生成下一个ID
   *
   * @return 生成的ID
   */
  public synchronized long nextId() {
    // 当前时间
    long currentTimeMillis = System.currentTimeMillis();

    if (currentTimeMillis == lastTimeMillis) {
      // 同一毫秒内，序列号自增
      clockSeq++;
      if (clockSeq > MAX_CLOCK_SEQ) {
        // 序列号超过最大值，等待下一毫秒
        clockSeq = 0;
        currentTimeMillis++;
      }
    } else if (currentTimeMillis < lastTimeMillis) {
      // 出现时间回拨，使用上次时间继续生成
      currentTimeMillis = lastTimeMillis;
      clockSeq++;
      if (clockSeq > MAX_CLOCK_SEQ) {
        clockSeq = 0;
        currentTimeMillis++;
      }
    } else {
      // 新的毫秒，序列号重置
      clockSeq = 0;
    }

    lastTimeMillis = currentTimeMillis;

    // 计算时间差
    long diffTimeMillis = currentTimeMillis - INITIAL_TIMESTAMP;

    // ID组成：时间（7+）| 毫秒内的时间自增 （00~99：2）| 机器ID（00 ~ 99：2）| 随机数（00~99：2）
    return diffTimeMillis * 1000000 + clockSeq * 10000 + workId * 100 + getRandomInt();
  }

  /**
   * 获取随机数 (0-99)
   *
   * @return 随机数
   */
  private int getRandomInt() {
    return ThreadLocalRandom.current().nextInt(100);
  }

  /** 重置生成器状态（主要用于测试） */
  public synchronized void reset() {
    lastTimeMillis = 0;
    clockSeq = 0;
  }
}
