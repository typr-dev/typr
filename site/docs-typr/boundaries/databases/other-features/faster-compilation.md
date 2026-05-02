---
title: Focus on fast(er) compiles
---

If you have a big database schema, Typr can easily produce tens to hundreds of thousands of lines of code.
And it's all full of implicit type class instances.

## Limit how much code is generated

The main thing you can affect is to ask for less code to be generated. 

This is done by [customizing selected relations](../customization/customize-selected-relations.md).

## The following measures have been taken to compile code fast 

- work around auto-derivation which takes precedence over instances in companion objects. 
For doobie it's the case the `Read`-instances, for example, are re-rederived instead of found on the companion object. 
- inlined ~all implicits to save the resolving time
- generate code which defines instances with methods instead of anonymous classes, in order to generate (much) less bytecode

The effect was to compile 80k sources in 20 seconds instead of more than a minute, across all supported scala versions.