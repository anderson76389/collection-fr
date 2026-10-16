(index) => {
    const text = document.body?.innerText ?? "";
    if (window._cf_chl_opt || /Vérification en cours/.test(text)) return { state: "waiting" };
    if (/Lecture impossible sur cet appareil|This device cannot display the reader/.test(text)) {
        return { state: "error", error: "Epsilon refuse encore cet appareil dans la WebView." };
    }
    const data = document.getElementById("app")?.dataset.page ?? document.querySelector('script[data-page="app"][type="application/json"]')?.textContent;
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
    if (plate.querySelector("button")) return { state: "error", error: `Le site n’a pas pu charger la page ${index + 1}.` };
    if (plate.querySelector(".animate-spin")) return { state: "waiting" };
    const canvas = plate.querySelector("canvas");
    if (!canvas) return { state: "waiting" };
    // Bitmap export is blocked by the site. Only locate the visible surface for Android.
    return { state: "ready", total: plates.length, element: canvas };
}
