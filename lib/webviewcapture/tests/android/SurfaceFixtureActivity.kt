package keiyoushi.lib.webviewcapture

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.WebView

class SurfaceFixtureActivity : Activity() {
    lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        setContentView(webView)
        webView.loadDataWithBaseURL(
            "https://fixture.invalid/",
            """
            <html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
            <body style="margin:0"><canvas width="400" height="600" style="width:100%;height:600px"></canvas>
            <script>
                Object.defineProperty(HTMLCanvasElement.prototype, "toDataURL", {
                    value() { throw new DOMException("Canvas is blocked.", "SecurityError"); },
                    writable: false, configurable: false
                });
                const canvas = document.querySelector("canvas");
                const worker = new Worker(URL.createObjectURL(new Blob([`
                    onmessage = ({data: canvas}) => {
                        const ctx = canvas.getContext("2d");
                        ctx.fillStyle = "#ff0000"; ctx.fillRect(0, 0, 400, 300);
                        ctx.fillStyle = "#0000ff"; ctx.fillRect(0, 300, 400, 300);
                        postMessage("ready");
                    };
                `], {type: "text/javascript"})));
                worker.onmessage = () => { document.body.dataset.ready = "true"; };
                const offscreen = canvas.transferControlToOffscreen();
                worker.postMessage(offscreen, [offscreen]);
            </script></body></html>
            """.trimIndent(),
            "text/html",
            "UTF-8",
            null,
        )
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
