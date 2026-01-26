package com.example.events.consumer

import cats.effect.IO
import com.example.events.OrderCancelled
import com.example.events.OrderEvents
import com.example.events.OrderPlaced
import com.example.events.OrderUpdated
import com.example.events.header.StandardHeaders
import java.lang.AutoCloseable
import java.time.Duration
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecords
import cats.syntax.all._
import scala.jdk.CollectionConverters._

/** Type-safe consumer for order-events topic */
case class OrderEventsConsumer(
  consumer: Consumer[String, OrderEvents],
  handler: OrderEventsHandler,
  topic: String = "order-events"
) extends AutoCloseable {
  /** Poll for messages and dispatch to handler, returning composed effect */
  def poll(timeout: Duration): IO[Unit] = {
    val records: ConsumerRecords[String, OrderEvents] = consumer.poll(timeout)
    return records.asScala.toList.traverse_(record => record.value match {
      case e: OrderCancelled => handler.handleOrderCancelled(record.key, e, StandardHeaders.fromHeaders(record.headers))
      case e: OrderPlaced => handler.handleOrderPlaced(record.key, e, StandardHeaders.fromHeaders(record.headers))
      case e: OrderUpdated => handler.handleOrderUpdated(record.key, e, StandardHeaders.fromHeaders(record.headers))
    })
  }

  /** Close the consumer */
  override def close: Unit = {
    consumer.close
  }
}