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
    customerRepo: CustomerRepo,
    addressRepo: AddressRepo,
    orderRepo: CustomerOrderRepo
):
  /** Create a customer with a shipping address and initial order */
  def createCustomerWithOrder(
      customer: CustomerRowUnsaved,
      address: AddressRowUnsaved,
      order: CustomerOrderRowUnsaved
  )(using c: Connection): CustomerOrderRow =
    // Insert customer first (no FK dependencies)
    val savedCustomer = customerRepo.insert(customer)

    // Insert address (FK to customer)
    val addressWithCustomer = address.copy(customerId = savedCustomer.id)
    val savedAddress = addressRepo.insert(addressWithCustomer)

    // Insert order (FK to customer, optional FK to address)
    val orderWithRefs = order.copy(
      customerId = savedCustomer.id,
      shippingAddressId = Some(savedAddress.id)
    )
    orderRepo.insert(orderWithRefs)

  /** Find all orders for a customer */
  def findOrdersForCustomer(customerId: CustomerId)(using c: Connection): List[CustomerOrderRow] =
    orderRepo.select
      .where(_.customerId === customerId)
      .toList
