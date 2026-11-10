@file:Suppress("ktlint:standard:package-name")

package org.chromium.support_lib_boundary

import android.webkit.WebView
import java.lang.reflect.InvocationHandler

// Source-local Chromium WebView glue. BoundaryInterfaceReflectionUtil.dupeMethod()
// resolves the declaring interface by its fully qualified name in the WebView APK.
// Therefore these names MUST exactly match Chromium's boundary interface names,
// not custom ScanManga* names. Keep this limited to the Scan-Manga source.
// The existing ProGuard rule preserves these names for reflection.
internal interface WebViewProviderFactoryBoundaryInterface {
    val supportedFeatures: Array<String>
    fun createWebView(webView: WebView): InvocationHandler
}

internal interface WebViewProviderBoundaryInterface {
    fun getProfile(): InvocationHandler
    fun addDocumentStartJavaScript(script: String, allowedOriginRules: Array<String>): InvocationHandler
}

internal interface ProfileBoundaryInterface {
    fun clearOriginMatchedHeader(headerName: String, headerValue: String)
    fun addOriginMatchedHeader(headerName: String, headerValue: String, originRules: Set<String>)
}
