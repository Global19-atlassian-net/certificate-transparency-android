/*
 * Copyright 2020 Babylon Partners Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.babylon.certificatetransparency.internal.utils

import java.io.IOException
import java.io.InputStream

/**
 * [InputStream] wrapper that throws an [IOException] if more than [maxSize] bytes are read
 */
internal class LimitedSizeInputStream(private val original: InputStream, private val maxSize: Long) : InputStream() {
    private var total: Long = 0

    override fun read() = original.read().also {
        if (it >= 0) incrementCounter(1)
    }

    override fun read(b: ByteArray) = read(b, 0, b.size)

    override fun read(b: ByteArray, off: Int, len: Int) = original.read(b, off, len).also {
        if (it >= 0) incrementCounter(it)
    }

    private fun incrementCounter(size: Int) {
        total += size.toLong()
        if (total > maxSize) throw IOException(MAX_SIZE_ERROR_MESSAGE)
    }
}

internal fun Exception.isTooBigException() = message == MAX_SIZE_ERROR_MESSAGE

private const val MAX_SIZE_ERROR_MESSAGE = "InputStream exceeded maximum size in bytes."
