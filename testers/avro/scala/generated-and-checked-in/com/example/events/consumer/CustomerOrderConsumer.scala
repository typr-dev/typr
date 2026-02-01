package com.example.events.consumer

import com.example.events.CustomerOrder
import com.example.events.header.StandardHeaders
import java.lang.AutoCloseable
import java.time.Duration
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecords

/** Type-safe consumer for customer-order topic */
case class CustomerOrderConsumer(
  consumer: Consumer[String, CustomerOrder],
  handler: CustomerOrderHandler,
  topic: String = "customer-order"
) extends AutoCloseable {
  /** Poll for messages and dispatch to handler */
  def poll(timeout: Duration): Unit = {
    val records: ConsumerRecords[String, CustomerOrder] = consumer.poll(timeout)
    records.forEach(record => { val key: String = record.key; val value: CustomerOrder = record.value; val headers: StandardHeaders = StandardHeaders.fromHeaders(record.headers); handler.handle(key, value, headers) })
  }

  /** Close the consumer */
  override def close: Unit = {
    consumer.close
  }
}