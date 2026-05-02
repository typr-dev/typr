---
title: Ready for Scala.js/native
---

If you want to move `Row` instances across the wire to a Scala.js app, you should be able to do so.

_Note that this hasn't been tested yet, so report back with results._

It's a rather manual process for now, where you can run Typr twice:
- once to generate JVM code, where you specify both a database and json library, and output files to a JVM-only source folder
- once to generate JS code, where you only specify a json library. You can write files to a JS-only source folder

If you use any date/time column types, it's likely you'll need to add [scala-java-time](https://github.com/cquiroz/scala-java-time) to your JS project.
