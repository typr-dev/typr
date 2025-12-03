package testdb;

import org.junit.Test;
import testdb.customtypes.Defaulted.UseDefault;
import testdb.orders.OrdersId;
import testdb.orders.OrdersRepoImpl;
import testdb.update_order_status.UpdateOrderStatusSqlRepoImpl;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Random;

import static org.junit.Assert.*;

public class UpdateOrderStatusSqlRepoTest {
    private final UpdateOrderStatusSqlRepoImpl repo = new UpdateOrderStatusSqlRepoImpl();
    private final OrdersRepoImpl ordersRepo = new OrdersRepoImpl();

    @Test
    public void updateOrderStatusToConfirmed() {
        WithConnection.run(c -> {
            var testInsert = new TestInsert(new Random(0));

            var customer = testInsert.Customers(
                new byte[]{1, 2, 3},
                "test1@example.com",
                "First",
                "Last",
                new UseDefault<>(), new UseDefault<>(), new UseDefault<>(),
                new UseDefault<>(), new UseDefault<>(), new UseDefault<>(),
                new UseDefault<>(), new UseDefault<>(), new UseDefault<>(),
                c
            );
            var order = testInsert.Orders(
                customer.customerId(),
                "ORD-001",
                new BigDecimal("100.00"),
                new BigDecimal("110.00"),
                new UseDefault<>(), new UseDefault<>(), new UseDefault<>(), new UseDefault<>(),
                new UseDefault<>(), new UseDefault<>(), new UseDefault<>(), new UseDefault<>(),
                new UseDefault<>(), new UseDefault<>(), new UseDefault<>(), new UseDefault<>(),
                new UseDefault<>(), new UseDefault<>(), new UseDefault<>(), new UseDefault<>(),
                new UseDefault<>(),
                c
            );

            assertEquals("pending", order.orderStatus());
            assertFalse(order.confirmedAt().isPresent());

            var rowsAffected = repo.apply("confirmed", order.orderId(), c);
            assertEquals(Integer.valueOf(1), rowsAffected);

            var updatedOrder = ordersRepo.selectById(order.orderId(), c);
            assertTrue(updatedOrder.isPresent());
            assertEquals("confirmed", updatedOrder.get().orderStatus());
            assertTrue(updatedOrder.get().confirmedAt().isPresent());
        });
    }

    @Test
    public void updateOrderStatusToShipped() {
        WithConnection.run(c -> {
            var testInsert = new TestInsert(new Random(0));

            var customer = testInsert.Customers(
                new byte[]{1, 2, 3},
                "test2@example.com",
                "First",
                "Last",
                new UseDefault<>(), new UseDefault<>(), new UseDefault<>(),
                new UseDefault<>(), new UseDefault<>(), new UseDefault<>(),
                new UseDefault<>(), new UseDefault<>(), new UseDefault<>(),
                c
            );
            var order = testInsert.Orders(
                customer.customerId(),
                "ORD-002",
                new BigDecimal("100.00"),
                new BigDecimal("110.00"),
                new UseDefault<>(), new UseDefault<>(), new UseDefault<>(), new UseDefault<>(),
                new UseDefault<>(), new UseDefault<>(), new UseDefault<>(), new UseDefault<>(),
                new UseDefault<>(), new UseDefault<>(), new UseDefault<>(), new UseDefault<>(),
                new UseDefault<>(), new UseDefault<>(), new UseDefault<>(), new UseDefault<>(),
                new UseDefault<>(),
                c
            );

            var rowsAffected = repo.apply("shipped", order.orderId(), c);
            assertEquals(Integer.valueOf(1), rowsAffected);

            var updatedOrder = ordersRepo.selectById(order.orderId(), c);
            assertTrue(updatedOrder.isPresent());
            assertEquals("shipped", updatedOrder.get().orderStatus());
            assertTrue(updatedOrder.get().shippedAt().isPresent());
        });
    }

    @Test
    public void updateNonExistentOrderReturnsZero() {
        WithConnection.run(c -> {
            var testInsert = new TestInsert(new Random(0));

            var customer = testInsert.Customers(
                new byte[]{1, 2, 3},
                "test3@example.com",
                "First",
                "Last",
                new UseDefault<>(), new UseDefault<>(), new UseDefault<>(),
                new UseDefault<>(), new UseDefault<>(), new UseDefault<>(),
                new UseDefault<>(), new UseDefault<>(), new UseDefault<>(),
                c
            );
            var order = testInsert.Orders(
                customer.customerId(),
                "ORD-003",
                new BigDecimal("100.00"),
                new BigDecimal("110.00"),
                new UseDefault<>(), new UseDefault<>(), new UseDefault<>(), new UseDefault<>(),
                new UseDefault<>(), new UseDefault<>(), new UseDefault<>(), new UseDefault<>(),
                new UseDefault<>(), new UseDefault<>(), new UseDefault<>(), new UseDefault<>(),
                new UseDefault<>(), new UseDefault<>(), new UseDefault<>(), new UseDefault<>(),
                new UseDefault<>(),
                c
            );

            var nonExistentId = new OrdersId(order.orderId().value().add(BigInteger.valueOf(9999)));
            var rowsAffected = repo.apply("confirmed", nonExistentId, c);
            assertEquals(Integer.valueOf(0), rowsAffected);
        });
    }
}
