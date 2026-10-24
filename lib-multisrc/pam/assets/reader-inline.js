// Reader v2 (October 2026) creates a blob: module Worker rather than a *.js worker.
// This path is enabled ONLY for Epsilon Scan, not other PAM extensions.
(bridge, workerPatch) => {
    const descriptor = Object.getOwnPropertyDescriptor(Document.prototype, "visibilityState");
    if (descriptor && descriptor.get) {
        Object.defineProperty(Document.prototype, "visibilityState", {
            ...descriptor, get: new Proxy(descriptor.get, { apply: () => "visible" }),
        });
    }
    const hidden = Object.getOwnPropertyDescriptor(Document.prototype, "hidden");
    if (hidden && hidden.get) {
        Object.defineProperty(Document.prototype, "hidden", {
            ...hidden, get: new Proxy(hidden.get, { apply: () => false }),
        });
    }

    const NativeObserver = window.IntersectionObserver;
    if (NativeObserver) window.IntersectionObserver = new Proxy(NativeObserver, {
        construct(target, args, newTarget) {
            const [callback, ...rest] = args;
            if (typeof callback !== "function") return Reflect.construct(target, args, newTarget);
            const observer = Reflect.construct(target, [callback, ...rest], newTarget);
            const observe = observer.observe.bind(observer);
            let first = true;
            observer.observe = (element) => {
                const result = observe(element);
                if (first && element instanceof Element) {
                    first = false;
                    queueMicrotask(() => {
                        const rect = element.getBoundingClientRect();
                        callback([{ target: element, isIntersecting: true, isVisible: true,
                            intersectionRatio: 1, boundingClientRect: rect, intersectionRect: rect,
                            rootBounds: null, time: performance.now() }], observer);
                    });
                }
                return result;
            };
            return observer;
        },
    });

    const NativeWorker = window.Worker;
    if (!NativeWorker) return;
    window.Worker = new Proxy(NativeWorker, {
        construct(target, args, newTarget) {
            const [url, options] = args;
            if (!options || options.type !== "module") return Reflect.construct(target, args, newTarget);
            const original = new URL(String(url), location.href).href;
            const moduleUrl = (source) => URL.createObjectURL(new Blob([source], { type: "text/javascript" }));
            const patch = moduleUrl("(" + workerPatch + ")();");
            const wrapper = moduleUrl("import " + JSON.stringify(patch) + ";\nimport " + JSON.stringify(original) + ";");
            const worker = Reflect.construct(target, [wrapper, options], newTarget);
            let published = false;
            worker.addEventListener("message", (event) => {
                const bytes = event.data && event.data.__epsReaderKeyBytes;
                if (published || !Array.isArray(bytes) || bytes.length !== 32 ||
                    bytes.some((x) => !Number.isInteger(x) || x < 0 || x > 255)) return;
                published = true;
                const contentKey = btoa(String.fromCharCode(...bytes));
                const started = Date.now();
                const report = () => {
                    const plates = document.querySelectorAll("[data-reader-plate]");
                    if (plates.length > 0 && Array.from(plates).every((p) => p.querySelector("canvas")) ||
                        Date.now() - started >= 5500 && plates.length > 0) {
                        window[bridge].post(JSON.stringify({reader: { contentKey, count: plates.length }}));
                    } else if (Date.now() - started < 30000) {
                        setTimeout(report, 200);
                    }
                };
                report();
            });
            return worker;
        },
    });
}
