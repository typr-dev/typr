package com.example.events.consumer

import com.example.events.DynamicValue
import com.example.events.header.StandardHeaders
import java.lang.AutoCloseable
import java.time.Duration
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerRecords

/** Type-safe consumer for dynamic-value topic */
case class DynamicValueConsumer(
  consumer: Consumer[String, DynamicValue],
  handler: DynamicValueHandler,
  topic: String = "dynamic-value"
) extends AutoCloseable {
  /** Poll for messages and dispatch to handler */
  def poll(timeout: Duration): Unit = {
    val records: ConsumerRecords[String, DynamicValue] = consumer.poll(timeout)
    records.forEach(record => { val key: String = record.key; val value: DynamicValue = record.value; val headers: StandardHeaders = StandardHeaders.fromHeaders(record.headers); handler.handle(key, value, headers) })
  }

  /** Close the consumer */
  override def close: Unit = {
    consumer.close
  }
}