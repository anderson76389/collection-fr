// Fetch the reader document from its already loaded browser session, keeping its
// cookies, user agent, transport and Referer. A challenge is never accepted as HTML.
() => {
    if (window._cf_chl_opt || window.__captcha?.needed) return { state: "challenge" };
    if (!document.querySelector("#full-reader, [id^=single-reader]")) return { state: "waiting" };
    const key = Symbol.for("japscan-reader-document");
    if (!window[key]) {
        const result = { state: "waiting" };
        window[key] = result;
        fetch(location.href, { credentials: "same-origin", headers: { Accept: "text/html" } })
            .then(async response => {
                if (!response.ok) throw new Error(`HTTP ${response.status}`);
                const html = await response.text();
                const page = new DOMParser().parseFromString(html, "text/html");
                if (!page.querySelector("#full-reader, [id^=single-reader]") || page.querySelector('script[src*="/cdn-cgi/challenge-platform/"]')) {
                    result.state = "challenge";
                    return;
                }
                result.html = html;
                result.state = "ready";
            })
            .catch(error => { result.state = "error"; result.error = String(error.message ?? error); });
    }
    return window[key];
}
