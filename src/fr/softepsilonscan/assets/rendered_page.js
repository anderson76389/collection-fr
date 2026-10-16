// Read a page the site's own reader has finished drawing. No key or prototype hooks.
(index) => {
    if (window._cf_chl_opt) return { state: "error", error: "Validez Cloudflare dans la WebView, puis relancez le chapitre." };
    const text = document.body?.innerText ?? "";
    if (/Lecture impossible sur cet appareil|This device cannot display the reader/.test(text)) {
        return { state: "error", error: "Le site refuse encore cet environnement de lecture (appareil non compatible)." };
    }
    const app = document.getElementById("app");
    const data = app?.dataset.page ?? document.querySelector('script[data-page="app"][type="application/json"]')?.textContent;
    if (data) {
        try {
            const props = JSON.parse(data).props;
            if (props.reader_v2 === false) return { state: "legacy" };
            if (props.video_url) return { state: "error", error: "Ce chapitre est une vidéo, pas une suite d’images." };
        } catch (_) {}
    }
    const plates = Array.from(document.querySelectorAll(".reader-plates > [data-reader-plate]"));
    const plate = plates[index];
    if (!plate) return { state: "waiting" };
    plate.scrollIntoView({ block: "center", behavior: "instant" });
    if (plate.querySelector("button")) {
        return { state: "error", error: `Le lecteur du site n’a pas pu charger la page ${index + 1}.` };
    }
    if (plate.querySelector(".animate-spin")) return { state: "waiting" };
    const canvas = plate.querySelector("canvas");
    // A fresh canvas is 300 x 150 but has no explicit dimensions and no decoded page.
    if (!canvas || !canvas.hasAttribute("width") || !canvas.hasAttribute("height") || !canvas.width || !canvas.height) {
        return { state: "waiting" };
    }
    try {
        const data = canvas.toDataURL("image/png");
        if (!data.startsWith("data:image/png;base64,")) return { state: "waiting" };
        return { state: "page", index, total: plates.length, data };
    } catch (_) {
        return { state: "error", error: `Le navigateur ne permet pas de transférer la page ${index + 1} vers Mihon.` };
    }
}
