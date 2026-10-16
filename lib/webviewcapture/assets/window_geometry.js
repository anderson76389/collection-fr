(locate, index, offset) => {
    const result = locate(index);
    if (result.state !== "ready") return result;
    const element = result.element;
    let rect = element.getBoundingClientRect();
    if (!(rect.width > 0 && rect.height > offset)) return { state: "waiting" };
    // Middle of the viewport avoids the reader's fixed top/bottom controls.
    const margin = window.innerHeight * 0.2;
    const target = rect.top + offset;
    if (Math.abs(target - margin) > 1) {
        window.scrollBy({ top: target - margin, left: 0, behavior: "instant" });
        rect = element.getBoundingClientRect();
    }
    const y = rect.top + offset;
    const height = Math.min(rect.height - offset, window.innerHeight * 0.8 - y);
    if (rect.left < 0 || rect.right > window.innerWidth + 0.5 || y < 0 || height <= 1) return { state: "waiting" };
    const hit = document.elementFromPoint(rect.left + rect.width / 2, y + height / 2);
    if (!hit || !element.contains(hit)) return { state: "waiting" };
    return { state: "tile", index, total: result.total, x: rect.left, y, width: rect.width,
        height, pageHeight: rect.height, viewportWidth: window.innerWidth };
}
