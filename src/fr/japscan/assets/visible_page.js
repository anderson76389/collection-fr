(index) => {
    const visible = node => node && node.getBoundingClientRect().width > 0 && getComputedStyle(node).visibility !== "hidden";
    if (window._cf_chl_opt) return { state: "waiting" };
    const full = document.getElementById("full-reader");
    if (visible(full)) {
        const pages = Array.from(full.querySelectorAll('div[id^="d-img-"]'))
            .sort((a, b) => Number(a.id.slice(6)) - Number(b.id.slice(6)));
        const element = pages[index];
        if (!element) return { state: "waiting" };
        element.scrollIntoView({ block: "center", behavior: "instant" });
        if (element.querySelector('.spinner-border, .spinner, [aria-busy="true"]')) return { state: "waiting" };
        const drawn = [element, ...element.querySelectorAll("*")].some(node =>
            (node.canvas?.width > 1 && node.canvas?.height > 1) ||
            (node.tagName === "CANVAS" && node.width > 1 && node.height > 1) ||
            (node.tagName === "IMG" && node.complete && node.naturalWidth > 1 && node.src.startsWith("blob:")));
        if (!drawn) return { state: "waiting" };
        const rect = element.getBoundingClientRect();
        if (rect.height <= 20) return { state: "waiting" };
        return { state: "ready", element, total: pages.length };
    }
    const single = document.querySelector('[id^="single-reader"]');
    const select = document.getElementById("pages");
    if (!visible(single) || !select?.options.length) return { state: "waiting" };
    if (select.selectedIndex !== index) {
        select.selectedIndex = index;
        select.dispatchEvent(new Event("change", { bubbles: true }));
        return { state: "waiting" };
    }
    const image = single.querySelector('img[src^="blob:"]');
    if (!image || !image.complete || image.naturalWidth <= 0) return { state: "waiting" };
    return { state: "ready", element: image, total: select.options.length };
}
