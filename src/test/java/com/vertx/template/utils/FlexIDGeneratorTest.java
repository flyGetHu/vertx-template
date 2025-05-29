package com.vertx.template.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * FlexID生成器测试类
 *
 * @author template
 * @since 1.0.0
 */
class FlexIDGeneratorTest {

  private FlexIDGenerator generator;

  @BeforeEach
  void setUp() {
    generator = FlexIDGenerator.getInstance();
    generator.reset(); // 重置状态
  }

  @Test
  void testSingleThreadGeneration() {
    // 测试单线程生成ID
    Set<Long> ids = new HashSet<>();

    for (int i = 0; i < 1000; i++) {
      long id = generator.nextId();
      assertTrue(id > 0, "生成的ID应该大于0");
      assertFalse(ids.contains(id), "生成的ID应该是唯一的");
      ids.add(id);
    }

    assertEquals(1000, ids.size(), "应该生成1000个唯一的ID");
  }

  @Test
  void testMultiThreadGeneration() throws InterruptedException {
    // 测试多线程生成ID
    int threadCount = 10;
    int idsPerThread = 100;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch latch = new CountDownLatch(threadCount);
    Set<Long> allIds = new HashSet<>();
    AtomicInteger duplicateCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
      executor.submit(
          () -> {
            try {
              Set<Long> threadIds = new HashSet<>();
              for (int j = 0; j < idsPerThread; j++) {
                long id = generator.nextId();
                threadIds.add(id);
              }

              synchronized (allIds) {
                for (Long id : threadIds) {
                  if (!allIds.add(id)) {
                    duplicateCount.incrementAndGet();
                  }
                }
              }
            } finally {
              latch.countDown();
            }
          });
    }

    latch.await();
    executor.shutdown();

    assertEquals(0, duplicateCount.get(), "不应该有重复的ID");
    assertEquals(threadCount * idsPerThread, allIds.size(), "应该生成正确数量的唯一ID");
  }

  @Test
  void testIdOrder() {
    // 测试ID的时间顺序性
    long previousId = 0;

    for (int i = 0; i < 100; i++) {
      long currentId = generator.nextId();
      assertTrue(currentId > previousId, "后生成的ID应该大于前面生成的ID");
      previousId = currentId;

      // 稍微延迟，确保时间差异
      try {
        Thread.sleep(1);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }

  @Test
  void testIdStructure() {
    // 测试ID的结构
    long id = generator.nextId();
    String idStr = String.valueOf(id);

    // FlexID应该是一个较长的数字
    assertTrue(idStr.length() >= 10, "FlexID应该至少有10位数字");
    assertTrue(id > 0, "FlexID应该是正数");
  }

  @Test
  void testReset() {
    // 测试重置功能
    long id1 = generator.nextId();
    generator.reset();
    long id2 = generator.nextId();

    // 重置后生成的ID可能会重复，但这是预期的行为
    assertTrue(id1 > 0, "第一个ID应该大于0");
    assertTrue(id2 > 0, "重置后的ID应该大于0");
  }
}
