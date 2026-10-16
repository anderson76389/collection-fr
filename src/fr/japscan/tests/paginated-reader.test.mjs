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
