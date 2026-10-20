@file:Suppress("ktlint:standard:package-name")

package org.chromium.support_lib_boundary

import android.webkit.WebView
import java.lang.reflect.InvocationHandler

internal interface WebViewProviderFactoryBoundaryInterface {
    fun createWebView(webView: WebView): InvocationHandler

    val webkitToCompatConverter: InvocationHandler
    val supportedFeatures: Array<String>
}
