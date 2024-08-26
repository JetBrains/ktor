/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

/**
 * An instance of the plugin installed to your application.
 * */
public class PluginInstance internal constructor(internal val builder: PluginBuilder<*>)
