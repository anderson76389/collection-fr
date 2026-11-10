@file:Suppress("ktlint:standard:package-name")

package org.chromium.support_lib_boundary

import android.webkit.WebView
import java.lang.reflect.InvocationHandler

// Source-local Chromium WebView glue. The existing repository ProGuard rule
// preserves method names in org.chromium.support_lib_boundary for reflection.
internal interface ScanMangaWebViewFactoryBoundary {
    val supportedFeatures: Array<String>
    fun createWebView(webView: WebView): InvocationHandler
}

internal interface ScanMangaWebViewProviderBoundary {
    fun getProfile(): InvocationHandler
    fun addDocumentStartJavaScript(script: String, allowedOriginRules: Array<String>): InvocationHandler
}

internal interface ScanMangaWebViewProfileBoundary {
    fun clearOriginMatchedHeader(headerName: String, headerValue: String)
    fun addOriginMatchedHeader(headerName: String, headerValue: String, originRules: Set<String>)
}
