package showcase

import java.sql.Connection
import showcase.showcase.customer.*
import showcase.showcase.address.*
import showcase.showcase.customer_order.*

/**
 * Multi-repo pattern: coordinate multiple repositories in a single transaction.
 * Typr generates low-level repos; you write higher-level business logic.
 */
class MultiRepoExample(
    private val customerRepo: CustomerRepo,
    private val addressRepo: AddressRepo,
    private val orderRepo: CustomerOrderRepo
) {
    /** Create a customer with a shipping address and initial order */
    fun createCustomerWithOrder(
        customer: CustomerRowUnsaved,
        address: AddressRowUnsaved,
        order: CustomerOrderRowUnsaved,
        c: Connection
    ): CustomerOrderRow {
        // Insert customer first (no FK dependencies)
        val savedCustomer = customerRepo.insert(customer, c)

        // Insert address (FK to customer)
        val addressWithCustomer = address.copy(customerId = savedCustomer.id)
        val savedAddress = addressRepo.insert(addressWithCustomer, c)

        // Insert order (FK to customer, optional FK to address)
        val orderWithRefs = order.copy(
            customerId = savedCustomer.id,
            shippingAddressId = savedAddress.id
        )
        return orderRepo.insert(orderWithRefs, c)
    }

    /** Find all orders for a customer */
    fun findOrdersForCustomer(customerId: CustomerId, c: Connection): List<CustomerOrderRow> {
        return orderRepo.select()
            .where { it.customerId.eq(customerId) }
            .toList(c)
    }
}
