package com.example.events.consumer

import com.example.events.OrderCancelled
import com.example.events.OrderEvents
import com.example.events.OrderPlaced
import com.example.events.OrderUpdated
import com.example.events.header.StandardHeaders
import java.lang.AutoCloseable
import java.time.Duration
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecords

/** Type-safe consumer for order-events topic */
case class OrderEventsConsumer(
  consumer: Consumer[String, OrderEvents],
  handler: OrderEventsHandler,
  topic: String = "order-events"
) extends AutoCloseable {
  /** Poll for messages and dispatch to handler */
  def poll(timeout: Duration): Unit = {
    val records: ConsumerRecords[String, OrderEvents] = consumer.poll(timeout)
    records.forEach(record => { val key: String = record.key; val value: OrderEvents = record.value; val headers: StandardHeaders = StandardHeaders.fromHeaders(record.headers); value match {
      case e: OrderCancelled => handler.handleOrderCancelled(key, e, headers)
      case e: OrderPlaced => handler.handleOrderPlaced(key, e, headers)
      case e: OrderUpdated => handler.handleOrderUpdated(key, e, headers)
    } })
  }

  /** Close the consumer */
  override def close: Unit = {
    consumer.close
  }
}