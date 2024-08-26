/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.server.engine

import kotlinx.io.files.*

internal val WORKING_DIRECTORY_PATH: String = SystemFileSystem.resolve(Path(".")).toString()
