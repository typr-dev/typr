package dev.typr.dsl;

import dev.typr.foundations.Connection;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * A mock Connection that throws on any method access. Useful for testing DSL mock repos where the
 * connection is never used.
 */
public class MockConnection {
  public static final Connection instance =
      (Connection)
          Proxy.newProxyInstance(
              MockConnection.class.getClassLoader(),
              new Class<?>[] {Connection.class},
              new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                  throw new UnsupportedOperationException(
                      "MockConnection: " + method.getName() + " should not be called");
                }
              });
}
