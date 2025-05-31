package com.vertx.template.middleware;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.vertx.template.middleware.common.Middleware;
import com.vertx.template.middleware.common.MiddlewareChain;
import com.vertx.template.middleware.common.MiddlewareResult;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** 中间件链测试类 */
class MiddlewareChainTest {

  private MiddlewareChain middlewareChain;

  @Mock private RoutingContext routingContext;

  @Mock private Middleware middleware1;

  @Mock private Middleware middleware2;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);

    middlewareChain = new MiddlewareChain();

    // 设置mock中间件的行为
    when(middleware1.getName()).thenReturn("TestMiddleware1");
    when(middleware1.getOrder()).thenReturn(100);
    when(middleware1.isEnabled()).thenReturn(true);
    when(middleware1.handle(any(RoutingContext.class))).thenReturn(MiddlewareResult.success());

    when(middleware2.getName()).thenReturn("TestMiddleware2");
    when(middleware2.getOrder()).thenReturn(200);
    when(middleware2.isEnabled()).thenReturn(true);
    when(middleware2.handle(any(RoutingContext.class))).thenReturn(MiddlewareResult.success());
  }

  @Test
  void testMiddlewareRegistration() {
    // 测试中间件注册
    middlewareChain.register(middleware1);
    middlewareChain.register(middleware2);

    assertEquals(2, middlewareChain.size());
    assertTrue(middlewareChain.getMiddlewareNames().contains("TestMiddleware1"));
    assertTrue(middlewareChain.getMiddlewareNames().contains("TestMiddleware2"));
  }

  @Test
  void testMiddlewareExecution() {
    // 注册中间件
    middlewareChain.register(middleware1);
    middlewareChain.register(middleware2);

    // 模拟中间件执行
    MiddlewareResult result = middlewareChain.execute(routingContext);

    assertNotNull(result);
    assertTrue(result.isSuccess());

    // 验证中间件被调用
    verify(middleware1).handle(routingContext);
    verify(middleware2).handle(routingContext);
  }

  @Test
  void testEmptyMiddlewareChain() {
    // 测试空的中间件链
    MiddlewareResult result = middlewareChain.execute(routingContext);

    assertNotNull(result);
    assertEquals(0, middlewareChain.size());

    // 验证空链返回成功结果
    assertTrue(result.isSuccess());
    assertEquals("无中间件需要执行", result.getMessage());
  }

  @Test
  void testMiddlewareOrder() {
    // 测试中间件执行顺序
    middlewareChain.register(middleware2); // order: 200
    middlewareChain.register(middleware1); // order: 100

    assertEquals(2, middlewareChain.size());
    // middleware1应该排在前面（order更小）
    assertEquals("TestMiddleware1", middlewareChain.getMiddlewareNames().get(0));
    assertEquals("TestMiddleware2", middlewareChain.getMiddlewareNames().get(1));
  }

  @Test
  void testDisabledMiddleware() {
    // 测试禁用的中间件不会被注册
    when(middleware1.isEnabled()).thenReturn(false);

    middlewareChain.register(middleware1);
    middlewareChain.register(middleware2);

    assertEquals(1, middlewareChain.size());
    assertTrue(middlewareChain.getMiddlewareNames().contains("TestMiddleware2"));
    assertFalse(middlewareChain.getMiddlewareNames().contains("TestMiddleware1"));
  }

  @Test
  void testMiddlewareFailure() {
    // 测试中间件执行失败
    when(middleware1.handle(any(RoutingContext.class)))
        .thenReturn(MiddlewareResult.failure("401", "认证失败"));

    middlewareChain.register(middleware1);
    middlewareChain.register(middleware2);

    MiddlewareResult result = middlewareChain.execute(routingContext);

    assertNotNull(result);
    // middleware1应该被调用，但middleware2不应该被调用（因为middleware1失败）
    verify(middleware1).handle(routingContext);
    verify(middleware2, never()).handle(routingContext);

    // 验证返回失败结果
    assertFalse(result.isSuccess());
    assertEquals("401", result.getStatusCode());
    assertEquals("认证失败", result.getMessage());
  }

  @Test
  void testMiddlewareStopChain() {
    // 测试中间件停止执行链条
    when(middleware1.handle(any(RoutingContext.class)))
        .thenReturn(MiddlewareResult.stop("缓存命中，停止执行"));

    middlewareChain.register(middleware1);
    middlewareChain.register(middleware2);

    MiddlewareResult result = middlewareChain.execute(routingContext);

    assertNotNull(result);
    // middleware1应该被调用，但middleware2不应该被调用（因为middleware1要求停止）
    verify(middleware1).handle(routingContext);
    verify(middleware2, never()).handle(routingContext);

    // 验证返回停止结果
    assertTrue(result.isSuccess());
    assertFalse(result.shouldContinueChain());
    assertEquals("缓存命中，停止执行", result.getMessage());
  }

  @Test
  void testMiddlewareException() {
    // 测试中间件抛出异常
    when(middleware1.handle(any(RoutingContext.class))).thenThrow(new RuntimeException("中间件异常"));

    middlewareChain.register(middleware1);
    middlewareChain.register(middleware2);

    MiddlewareResult result = middlewareChain.execute(routingContext);

    assertNotNull(result);
    // middleware1应该被调用，但middleware2不应该被调用（因为middleware1抛出异常）
    verify(middleware1).handle(routingContext);
    verify(middleware2, never()).handle(routingContext);

    // 验证返回错误结果
    assertFalse(result.isSuccess());
    assertEquals("500", result.getStatusCode());
    assertTrue(result.getMessage().contains("中间件执行异常"));
  }

  @Test
  void testGetMiddleware() {
    // 测试获取指定索引的中间件
    middlewareChain.register(middleware1);
    middlewareChain.register(middleware2);

    assertEquals(middleware1, middlewareChain.getMiddleware(0));
    assertEquals(middleware2, middlewareChain.getMiddleware(1));
    assertNull(middlewareChain.getMiddleware(2));
    assertNull(middlewareChain.getMiddleware(-1));
  }

  @Test
  void testContainsMiddleware() {
    // 测试检查是否包含指定名称的中间件
    middlewareChain.register(middleware1);
    middlewareChain.register(middleware2);

    assertTrue(middlewareChain.containsMiddleware("TestMiddleware1"));
    assertTrue(middlewareChain.containsMiddleware("TestMiddleware2"));
    assertFalse(middlewareChain.containsMiddleware("NonExistentMiddleware"));
  }

  @Test
  void testClear() {
    // 测试清空中间件
    middlewareChain.register(middleware1);
    middlewareChain.register(middleware2);
    assertEquals(2, middlewareChain.size());

    middlewareChain.clear();
    assertEquals(0, middlewareChain.size());
    assertTrue(middlewareChain.getMiddlewareNames().isEmpty());
  }
}
