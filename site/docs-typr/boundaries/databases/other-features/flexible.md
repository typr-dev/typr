---
title: Compatible with your project
---

Much care has been taken while developing Typr to support many existing projects.

If you have a Scala 2 project with thousands of lines of database code, you have the option
of integrating Typr piece-by-piece into your code base.

## Developed in the Scala 2/3 shared subset

Everything works on Scala 2.13 and 3.3. 

For *2.13* you need to set add `-Xsource:3` to `scalacOptions`. The shared subsets shrinks in size for every Scala 3 release, unfortunately.

## Bring your own DB library

Not wanting to invent all the wheels, Typr expects you to already use a Scala database library.

For now you have the option of `doobie`, `anorm` or `zio-jdbc`, with `skunk` to follow. Maybe also plain `jdbc` mode later.

## Bring your own JSON library

In the same manner, you can generate JSON codecs for the library you already use, see [json](../other-features/json.md)