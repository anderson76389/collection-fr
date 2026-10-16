import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import vm from 'node:vm';

const kotlin = await readFile(new URL('../src/eu/kanade/tachiyomi/extension/fr/japscan/ReaderScripts.kt', import.meta.url), 'utf8');
const raw = kotlin.match(/internal fun paginatedDriver\(interfaceName: String\) = \$\$"""([\s\S]*?)"""\.trimIndent\(\)/)[1];
const script = raw.replaceAll('$${interfaceName}', 'test');

async function read(total, missing = -1) {
    let current = 1;
    let navigations = 0;
    const saved = [];
    let complete;
    const window = {
        __japscanLastBlob: `blob:${total}`,
        test_savePage: { post: data => saved.push(data) },
        test_passDone: { post: data => { complete = JSON.parse(data); } },
    };
    function navigate(delta) {
        navigations++;
        current = Math.max(1, Math.min(total, current + delta));
        window.__japscanLastBlob = current === missing ? null : `blob:${current}`;
    }
    class FileReader {
        readAsDataURL(blob) {
            this.result = `data:image/png;base64,page${blob.id}`;
            this.onload();
        }
    }
    const document = {
        getElementById: id => id === 'pages' ? { options: Array(total) } : id === 'block-right' ? { click: () => navigate(1) } : { click: () => navigate(-1) },
    };
    await vm.runInNewContext(script, {
        window, document, FileReader,
        console: { log() {} },
        setTimeout: callback => queueMicrotask(callback),
        URL: { revokeObjectURL() {} },
        fetch: async url => ({ blob: async () => ({ id: url.split(':')[1] }) }),
    });
    return { saved, complete, navigations };
}

test('a single-page chapter preserves its only rendered blob without navigating', async () => {
    const result = await read(1);
    assert.deepEqual(result.saved, ['data:image/png;base64,page1']);
    assert.equal(result.navigations, 0);
    assert.deepEqual(result.complete, { pages: 1, expected: 1 });
});

test('multi-page chapters preserve page order despite the last-page pre-render', async () => {
    const result = await read(3);
    assert.deepEqual(result.saved, [1, 2, 3].map(i => `data:image/png;base64,page${i}`));
    assert.deepEqual(result.complete, { pages: 3, expected: 3 });
});

test('a missing page stays incomplete instead of reporting successful completion', async () => {
    const result = await read(3, 2);
    assert.deepEqual(result.complete, { pages: 2, expected: 3 });
});

test('capture waits for the async paginated reader to produce its first blob', () => {
    const raw = kotlin.match(/internal fun readerPreparation\(webtoon: Boolean\) = """([\s\S]*?)"""\.trimIndent\(\)/)[1];
    const script = raw.replaceAll('$webtoon', 'false');
    const context = { window: {}, document: { getElementById: () => ({ options: [1, 2] }) } };
    assert.equal(vm.runInNewContext(script, context), false);
    context.window.__japscanLastBlob = 'blob:1';
    assert.equal(vm.runInNewContext(script, context), true);
});

test('the first webtoon container alone is insufficient until a canvas has been drawn', () => {
    const raw = kotlin.match(/internal fun readerPreparation\(webtoon: Boolean\) = """([\s\S]*?)"""\.trimIndent\(\)/)[1];
    const script = raw.replaceAll('$webtoon', 'true');
    const node = { tagName: 'CANVAS', width: 1080, height: 2000 };
    const first = { scrollIntoView() {}, querySelectorAll: () => [node] };
    const context = { window: {}, document: { querySelector: () => first } };
    assert.equal(vm.runInNewContext(script, context), false);
    context.window.__japscanLastDraw = 123;
    assert.equal(vm.runInNewContext(script, context), true);
});

const browserDocumentScript = `(${await readFile(new URL('../assets/verified_reader_html.js', import.meta.url), 'utf8')})`;
function browserDocumentFixture({ challenge = false, mounted = true, returnedReader = true, status = 200 } = {}) {
    let calls = 0;
    let options;
    class DOMParser {
        parseFromString() {
            return { querySelector: selector => selector.startsWith('script') ? null : returnedReader ? {} : null };
        }
    }
    const context = vm.createContext({
        window: challenge ? { _cf_chl_opt: {} } : {},
        document: { querySelector: () => mounted ? {} : null },
        location: { href: 'https://www.japscan.foo/manga/example/1/' },
        DOMParser,
        fetch: async (_, opts) => {
            calls++;
            options = opts;
            return { ok: status === 200, status, text: async () => '<html>reader</html>' };
        },
    });
    return { read: vm.runInContext(browserDocumentScript, context), get calls() { return calls; }, get options() { return options; } };
}

test('reader HTML is fetched once through the browser session with same-origin cookies', async () => {
    const env = browserDocumentFixture();
    assert.equal(env.read().state, 'waiting');
    await new Promise(setImmediate);
    assert.equal(env.read().state, 'ready');
    assert.equal(env.read().html, '<html>reader</html>');
    assert.equal(env.calls, 1);
    assert.equal(env.options.credentials, 'same-origin');
});

test('Cloudflare blocks transfer until the user completes verification', () => {
    const env = browserDocumentFixture({ challenge: true });
    assert.equal(env.read().state, 'challenge');
    assert.equal(env.calls, 0);
});

test('browser-session retrieval waits for a reader instead of fetching an unrelated page', () => {
    const env = browserDocumentFixture({ mounted: false });
    assert.equal(env.read().state, 'waiting');
    assert.equal(env.calls, 0);
});

test('a challenge returned by the browser fetch is not mistaken for a reader', async () => {
    const env = browserDocumentFixture({ returnedReader: false });
    env.read();
    await new Promise(setImmediate);
    assert.equal(env.read().state, 'challenge');
});

test('a browser HTTP failure remains an explicit error', async () => {
    const env = browserDocumentFixture({ status: 403 });
    env.read();
    await new Promise(setImmediate);
    assert.equal(env.read().state, 'error');
    assert.match(env.read().error, /HTTP 403/);
});
