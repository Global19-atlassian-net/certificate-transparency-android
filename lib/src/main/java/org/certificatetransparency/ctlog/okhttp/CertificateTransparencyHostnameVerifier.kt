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

package org.certificatetransparency.ctlog.okhttp

import org.certificatetransparency.ctlog.LogSignatureVerifier
import org.certificatetransparency.ctlog.domain.datasource.DataSource
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSession
import javax.net.ssl.X509TrustManager

class CertificateTransparencyHostnameVerifier private constructor(
    private val delegate: HostnameVerifier,
    trustManager: X509TrustManager?,
    logListDataSource: DataSource<Map<String, LogSignatureVerifier>>?
) : CertificateTransparencyBase(trustManager, logListDataSource), HostnameVerifier {

    override fun verify(host: String, sslSession: SSLSession): Boolean {
        if (delegate.verify(host, sslSession)) {
            try {
                val cleanedCerts = cleaner.clean(sslSession.peerCertificates.toList(), host)

                return isGood(cleanedCerts)
            } catch (e: SSLException) {
                throw RuntimeException(e)
            }
        }

        return false
    }

    class Builder(val delegate: HostnameVerifier) {
        var trustManager: X509TrustManager? = null

        var logListDataSource: DataSource<Map<String, LogSignatureVerifier>>? = null

        fun build() = CertificateTransparencyHostnameVerifier(delegate, trustManager, logListDataSource)
    }
}