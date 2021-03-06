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

package com.babylon.certificatetransparency.internal.loglist

import com.babylon.certificatetransparency.datasource.DataSource
import com.babylon.certificatetransparency.internal.utils.isTooBigException
import com.babylon.certificatetransparency.loglist.RawLogListResult
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async

internal class LogListNetworkDataSource(
    private val logService: LogListService
) : DataSource<RawLogListResult> {

    override val coroutineContext = GlobalScope.coroutineContext

    @Suppress("ReturnCount")
    override suspend fun get(): RawLogListResult {
        val logListJob = async { logService.getLogList() }
        val signatureJob = async { logService.getLogListSignature() }

        val logListJson = try {
            logListJob.await()
        } catch (expected: Exception) {
            return if (expected.isTooBigException()) RawLogListJsonFailedTooBig else RawLogListJsonFailedLoadingWithException(expected)
        }

        val signature = try {
            signatureJob.await()
        } catch (expected: Exception) {
            return if (expected.isTooBigException()) RawLogListSigFailedTooBig else RawLogListSigFailedLoadingWithException(expected)
        }

        return RawLogListResult.Success(logListJson, signature)
    }

    override suspend fun isValid(value: RawLogListResult?) = value is RawLogListResult.Success

    override suspend fun set(value: RawLogListResult) = Unit
}
