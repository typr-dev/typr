package showcase;

import java.sql.Connection;
import java.util.List;
import java.util.Optional;
import showcase.showcase.customer.*;
import showcase.showcase.address.*;
import showcase.showcase.customer_order.*;

/**
 * Multi-repo pattern: coordinate multiple repositories in a single transaction.
 * Typr generates low-level repos; you write higher-level business logic.
 */
public class MultiRepoExample {
    private final CustomerRepo customerRepo;
    private final AddressRepo addressRepo;
    private final CustomerOrderRepo orderRepo;

    public MultiRepoExample(
            CustomerRepo customerRepo,
            AddressRepo addressRepo,
            CustomerOrderRepo orderRepo) {
        this.customerRepo = customerRepo;
        this.addressRepo = addressRepo;
        this.orderRepo = orderRepo;
    }

    /** Create a customer with a shipping address and initial order */
    public CustomerOrderRow createCustomerWithOrder(
            CustomerRowUnsaved customer,
            AddressRowUnsaved address,
            CustomerOrderRowUnsaved order,
            Connection c) {
        // Insert customer first (no FK dependencies)
        CustomerRow savedCustomer = customerRepo.insert(customer, c);

        // Insert address (FK to customer)
        AddressRowUnsaved addressWithCustomer = address.withCustomerId(savedCustomer.id());
        AddressRow savedAddress = addressRepo.insert(addressWithCustomer, c);

        // Insert order (FK to customer, optional FK to address)
        CustomerOrderRowUnsaved orderWithRefs = order
            .withCustomerId(savedCustomer.id())
            .withShippingAddressId(Optional.of(savedAddress.id()));
        return orderRepo.insert(orderWithRefs, c);
    }

    /** Find all orders for a customer with their shipping addresses */
    public List<CustomerOrderRow> findOrdersWithAddresses(CustomerId customerId, Connection c) {
        return orderRepo.select()
            .where(o -> o.customerId().eq(customerId))
            .toList(c);
    }
}
