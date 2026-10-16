// The site's Inertia page data, and the site's own API calls made from that page.
(bridge, request, reader = false) => {
    if (window._cf_chl_opt) return "challenge";
    const post = (result) => window[bridge].post(JSON.stringify(result));
    if (reader && /Lecture impossible sur cet appareil|This device cannot display the reader/.test(document.body?.innerText ?? "")) {
        post({ error: "Le lecteur Epsilon refuse cet environnement de lecture. Vérifiez ce chapitre dans la WebView avec Android System WebView à jour ; une validation Cloudflare seule ne suffit pas." });
        return null;
    }
    // An error page, once it has loaded without turning out to be a challenge.
    const status = performance.getEntriesByType("navigation")[0]?.responseStatus ?? 0;
    if (status >= 400 && document.readyState === "complete") {
        post({ error: `HTTP error ${status}`, status });
        return null;
    }
    const app = document.getElementById("app");
    const data = app?.dataset.page ?? document.querySelector('script[data-page="app"][type="application/json"]')?.textContent;
    if (!data) return null;
    const started = Symbol.for(`${bridge}-read`);
    if (window[started]) return null;
    window[started] = true;

    const xsrf = decodeURIComponent(document.cookie.match(/(?:^|; )XSRF-TOKEN=([^;]*)/)?.[1] ?? "");
    const api = async (path) => {
        const response = await fetch(path, {
            headers: { Accept: "application/json, text/plain, */*", "X-Requested-With": "XMLHttpRequest", "X-XSRF-TOKEN": xsrf },
            credentials: "same-origin",
        });
        if (!response.ok) throw new Error(`HTTP error ${response.status}`);
        return response.json();
    };

    (async () => {
        const page = JSON.parse(data);
        const result = { page };
        if (request.fetch) result.fetched = await api(request.fetch);
        const serie = page.props.serie;
        if (request.chapters && serie && (serie.chapters == null || serie.chapters.length === 0)) {
            result.chapters = [];
            for (let n = 1, last = 1; n <= last; n++) {
                const list = await api(`/api/v1/series/${serie.uid}/chapters?page=${n}&sort=asc`);
                result.chapters.push(...list.items);
                last = list.last_page;
            }
        }
        post(result);
    })().catch((e) => post({ error: String(e?.message ?? e) }));
    return null;
}
