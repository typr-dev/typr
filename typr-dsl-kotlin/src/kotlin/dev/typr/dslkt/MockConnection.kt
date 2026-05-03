package dev.typr.dslkt

import dev.typr.foundationskt.Connection

object MockConnection {
    val instance: Connection = Connection(dev.typr.dsl.MockConnection.instance)
}
