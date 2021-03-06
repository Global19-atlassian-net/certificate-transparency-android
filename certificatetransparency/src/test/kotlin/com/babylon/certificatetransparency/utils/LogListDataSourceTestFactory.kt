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

package com.babylon.certificatetransparency.utils

import com.babylon.certificatetransparency.datasource.DataSource
import com.babylon.certificatetransparency.internal.loglist.model.v2.Log
import com.babylon.certificatetransparency.internal.loglist.model.v2.LogListV2
import com.babylon.certificatetransparency.internal.utils.Base64
import com.babylon.certificatetransparency.internal.utils.PublicKeyFactory
import com.babylon.certificatetransparency.loglist.LogListResult
import com.babylon.certificatetransparency.loglist.LogServer
import com.google.gson.GsonBuilder
import kotlinx.coroutines.GlobalScope

object LogListDataSourceTestFactory {

    val logListDataSource: DataSource<LogListResult> by lazy {
        // Collection of CT logs that are trusted from https://www.gstatic.com/ct/log_list/log_list.json
        val json = TestData.file(TestData.TEST_LOG_LIST_JSON).readText()
        val trustedLogKeys = GsonBuilder().create().fromJson(json, LogListV2::class.java).operators.flatMap { it.logs.map(Log::key) }

        val list = LogListResult.Valid(
            trustedLogKeys.map { Base64.decode(it) }.map {
                LogServer(PublicKeyFactory.fromByteArray(it))
            }
        )

        object : DataSource<LogListResult> {
            override suspend fun get() = list

            override suspend fun set(value: LogListResult) = Unit

            override val coroutineContext = GlobalScope.coroutineContext
        }
    }

    val emptySource: DataSource<LogListResult> by lazy {
        object : DataSource<LogListResult> {
            override suspend fun get() = LogListResult.Valid(emptyList())

            override suspend fun set(value: LogListResult) = Unit

            override val coroutineContext = GlobalScope.coroutineContext
        }
    }

    val nullSource: DataSource<LogListResult> by lazy {
        object : DataSource<LogListResult> {
            override suspend fun get(): LogListResult? = null

            override suspend fun set(value: LogListResult) = Unit

            override val coroutineContext = GlobalScope.coroutineContext
        }
    }
}
