/*
 * Copyright 2018 Babylon Healthcare Services Limited
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

import com.babylon.certificatetransparency.internal.utils.Base64
import com.babylon.certificatetransparency.loglist.LogListResult
import com.babylon.certificatetransparency.utils.TestData
import com.babylon.certificatetransparency.utils.assertIsA
import com.nhaarman.mockitokotlin2.argThat
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Retrofit
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.Signature
import javax.net.ssl.SSLPeerUnverifiedException

class LogListNetworkDataSourceTest {

    private val mockInterceptor = mock<Interceptor>()

    private val client: OkHttpClient = OkHttpClient.Builder().addInterceptor(mockInterceptor).build()
    private val retrofit = Retrofit.Builder().client(client).baseUrl("http://ctlog/").build()
    private val logListService: LogListService = retrofit.create(LogListService::class.java)

    private fun expectInterceptor(url: String, jsonResponse: String) {
        whenever(mockInterceptor.intercept(argThat { request().url().toString() == url })).then {

            val chain = it.arguments[0] as Interceptor.Chain

            Response.Builder()
                .body(ResponseBody.create(MediaType.parse("application/json"), jsonResponse))
                .request(chain.request())
                .protocol(Protocol.HTTP_2)
                .code(200)
                .message("")
                .build()
        }
    }

    private fun expectInterceptorHttpNotFound(url: String) {
        whenever(mockInterceptor.intercept(argThat { request().url().toString() == url })).then {

            val chain = it.arguments[0] as Interceptor.Chain

            Response.Builder()
                .body(ResponseBody.create(MediaType.parse("application/octet-stream"), ByteArray(0)))
                .request(chain.request())
                .protocol(Protocol.HTTP_2)
                .code(404)
                .message("")
                .build()
        }
    }

    private fun expectInterceptorSSLException(url: String) {
        whenever(mockInterceptor.intercept(argThat { request().url().toString() == url })).then {
            throw SSLPeerUnverifiedException("Mock throwing exception")
        }
    }

    private fun expectInterceptor(url: String, byteResponse: ByteArray) {
        whenever(mockInterceptor.intercept(argThat { request().url().toString() == url })).then {

            val chain = it.arguments[0] as Interceptor.Chain

            Response.Builder()
                .body(ResponseBody.create(MediaType.parse("application/octet-stream"), byteResponse))
                .request(chain.request())
                .protocol(Protocol.HTTP_2)
                .code(200)
                .message("")
                .build()
        }
    }

    @Test
    fun `verifies signature`() = runBlocking {
        // given we have a valid json file and signature
        expectInterceptor("http://ctlog/log_list.json", json)
        expectInterceptor("http://ctlog/log_list.sig", sig)

        // when we ask for data
        val result = LogListNetworkDataSource(logListService).get()

        // then 32 items are returned
        require(result is LogListResult.Valid)
        assertEquals(32, result.servers.size)
        assertEquals("pFASaQVaFVReYhGrN7wQP2KuVXakXksXFEU+GyIQaiU=", Base64.toBase64String(result.servers[0].id))
    }

    @Test
    fun `returns Invalid if json incomplete`() = runBlocking {
        // given we have an invalid json file and valid signature
        expectInterceptor("http://ctlog/log_list.json", jsonIncomplete)
        val keyPair = generateKeyPair()
        val signature = calculateSignature(keyPair.private, jsonIncomplete.toByteArray())
        expectInterceptor("http://ctlog/log_list.sig", signature)

        // when we ask for data
        val result = LogListNetworkDataSource(logListService, keyPair.public).get()

        // then invalid is returned
        assertIsA<JsonFormat>(result)
    }

    @Test
    fun `returns Invalid if signature invalid`() = runBlocking {
        // given we have a valid json file and signature
        expectInterceptor("http://ctlog/log_list.json", json)
        expectInterceptor("http://ctlog/log_list.sig", ByteArray(512))

        // when we ask for data
        val result = LogListNetworkDataSource(logListService).get()

        // then invalid is returned
        assertIsA<SignatureVerificationFailed>(result)
    }

    @Test
    fun `returns Invalid if signature corrupt`() = runBlocking {
        // given we have a valid json file and signature
        expectInterceptor("http://ctlog/log_list.json", json)
        expectInterceptor("http://ctlog/log_list.sig", ByteArray(32))

        // when we ask for data
        val result = LogListNetworkDataSource(logListService).get()

        // then invalid is returned
        assertIsA<SignatureVerificationFailed>(result)
    }

    @Test
    fun `returns Invalid when log_list json not found`() = runBlocking {
        // given we have a valid json file and signature
        expectInterceptorHttpNotFound("http://ctlog/log_list.json")
        expectInterceptor("http://ctlog/log_list.sig", sig)

        // when we ask for data
        val result = LogListNetworkDataSource(logListService).get()

        // then invalid is returned
        assertIsA<LogListJsonFailedLoading>(result)
    }

    @Test
    fun `returns Invalid when log_list sig not found`() = runBlocking {
        // given we have a valid json file and signature
        expectInterceptor("http://ctlog/log_list.json", json)
        expectInterceptorHttpNotFound("http://ctlog/log_list.sig")

        // when we ask for data
        val result = LogListNetworkDataSource(logListService).get()

        // then invalid is returned
        assertIsA<LogListSigFailedLoading>(result)
    }

    @Test
    fun `returns Invalid when log_list json has SslException`() = runBlocking {
        // given we have a valid signature and an exception when accessing the log list
        expectInterceptorSSLException("http://ctlog/log_list.json")
        expectInterceptor("http://ctlog/log_list.sig", sig)

        // when we ask for data
        val result = LogListNetworkDataSource(logListService).get()

        // then invalid is returned
        assertIsA<LogListJsonFailedLoadingWithException>(result)
    }

    @Test
    fun `returns Invalid when log_list sig has SslException`() = runBlocking {
        // given we have a valid json file and an exception when accessing the signature
        expectInterceptor("http://ctlog/log_list.json", json)
        expectInterceptorSSLException("http://ctlog/log_list.sig")

        // when we ask for data
        val result = LogListNetworkDataSource(logListService).get()

        // then invalid is returned
        assertIsA<LogListSigFailedLoadingWithException>(result)
    }

    @Test
    fun `validUntil null when not disqualified or no FinalTreeHead`() = runBlocking {
        // given we have a valid json file and signature
        expectInterceptor("http://ctlog/log_list.json", jsonValidUntil)
        val keyPair = generateKeyPair()
        val signature = calculateSignature(keyPair.private, jsonValidUntil.toByteArray())
        expectInterceptor("http://ctlog/log_list.sig", signature)

        // when we ask for data
        val result = LogListNetworkDataSource(logListService, keyPair.public).get()

        // then validUntil is set to the the STH timestamp
        require(result is LogListResult.Valid)
        val logServer = result.servers[0]
        assertNull(logServer.validUntil)
    }

    @Test
    fun `validUntil set from Sth`() = runBlocking {
        // given we have a valid json file and signature
        expectInterceptor("http://ctlog/log_list.json", jsonValidUntil)
        val keyPair = generateKeyPair()
        val signature = calculateSignature(keyPair.private, jsonValidUntil.toByteArray())
        expectInterceptor("http://ctlog/log_list.sig", signature)

        // when we ask for data
        val result = LogListNetworkDataSource(logListService, keyPair.public).get()

        // then validUntil is set to the the STH timestamp
        require(result is LogListResult.Valid)
        val logServer = result.servers[2]
        assertNotNull(logServer.validUntil)
        assertEquals(1480512258330, logServer.validUntil)
    }

    @Test
    fun `validUntil set from disqualified`() = runBlocking {
        // given we have a valid json file and signature
        expectInterceptor("http://ctlog/log_list.json", jsonValidUntil)
        val keyPair = generateKeyPair()
        val signature = calculateSignature(keyPair.private, jsonValidUntil.toByteArray())
        expectInterceptor("http://ctlog/log_list.sig", signature)

        // when we ask for data
        val result = LogListNetworkDataSource(logListService, keyPair.public).get()

        // then validUntil is set to the the STH timestamp
        require(result is LogListResult.Valid)
        val logServer = result.servers[1]
        assertNotNull(logServer.validUntil)
        assertEquals(1475637842000, logServer.validUntil)
    }

    private fun calculateSignature(privateKey: PrivateKey, data: ByteArray): ByteArray {
        return Signature.getInstance("SHA256WithRSA").apply {
            initSign(privateKey)
            update(data)
        }.sign()
    }

    private fun generateKeyPair(): KeyPair {
        return KeyPairGenerator.getInstance("RSA").apply {
            initialize(1024)
        }.generateKeyPair()
    }

    companion object {
        private val json = TestData.file(TestData.TEST_LOG_LIST_JSON).readText()
        private val sig = TestData.file(TestData.TEST_LOG_LIST_SIG).readBytes()

        private val jsonIncomplete = TestData.file(TestData.TEST_LOG_LIST_JSON_INCOMPLETE).readText()
        private val jsonValidUntil = TestData.file(TestData.TEST_LOG_LIST_JSON_VALID_UNTIL).readText()
    }
}