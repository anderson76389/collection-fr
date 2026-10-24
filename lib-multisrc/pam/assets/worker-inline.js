// Runs as an imported module before the site's inline reader worker.
// Send numeric key bytes to the owning Worker instead of BroadcastChannel:
// the October reader now scrambles 32-byte base64 BroadcastChannel messages.
() => {
    const original = SubtleCrypto.prototype.importKey;
    const nativePost = self.postMessage.bind(self);
    SubtleCrypto.prototype.importKey = new Proxy(original, {
        apply(target, thisArg, args) {
            try {
                const [format, source, algorithm] = args;
                const name = (algorithm && algorithm.name) || algorithm;
                if (format === "raw" && name === "HKDF" && source && source.byteLength === 32) {
                    const view = ArrayBuffer.isView(source)
                        ? new Uint8Array(source.buffer, source.byteOffset, source.byteLength)
                        : new Uint8Array(source);
                    nativePost({ __epsReaderKeyBytes: Array.from(view) });
                }
            } catch (ignored) {}
            return Reflect.apply(target, thisArg, args);
        },
    });
}
