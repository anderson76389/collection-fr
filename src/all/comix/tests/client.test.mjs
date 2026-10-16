import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import vm from 'node:vm';
import test from 'node:test';

const assets = new URL('../assets/', import.meta.url);
const scripts = Object.fromEntries(await Promise.all(
    ['chapter_list', 'chapter_pages', 'site_api'].map(async name => [
        name, `(${await readFile(new URL(`${name}.js`, assets), 'utf8')})`,
    ]),
));

function environment() {
    const listeners = {};
    return {
        setTimeout, clearTimeout,
        document: {
            readyState: 'complete',
            addEventListener: (name, fn) => { listeners[name] = fn; },
        },
        window: { innerWidth: 400, innerHeight: 800, scrollY: 0 },
        swipe: () => listeners.touchend({ isTrusted: true }),
    };
}

const chapter = id => ({ id, number: id, url: `/title/abc-example/${id}-chapter-${id}` });
async function list(pages, known = []) {
    const env = environment();
    const requests = [];
    const payload = await new Promise(resolve => {
        vm.runInNewContext(scripts.chapter_list, env)(
            () => async () => async (id, params) => {
                requests.push({ id, page: params.page });
                const response = pages[params.page - 1];
                if (response instanceof Error) throw response;
                return response;
            },
            known, 'abc', '/title/abc-', 1000, env.swipe, resolve,
        );
    });
    return { ...JSON.parse(payload), requests };
}

test('all pages are retained, including chapters outside the initial site page', async () => {
    const result = await list([
        { items: [chapter(3), chapter(2)], meta: { lastPage: 2, total: 3 } },
        { items: [chapter(1)], meta: { lastPage: 2, total: 3 } },
    ]);
    assert.equal(result.complete, true);
    assert.deepEqual(result.chapters.map(c => c.id), [3, 2, 1]);
    assert.deepEqual(result.requests, [{ id: 'abc', page: 1 }, { id: 'abc', page: 2 }]);
});

test('502 after a good page keeps the chapters and marks the result incomplete', async () => {
    const error = Object.assign(new Error('gateway'), { response: { status: 502 } });
    const result = await list([
        { items: [chapter(3)], meta: { lastPage: 2, total: 2 } }, error,
    ]);
    assert.equal(result.complete, false);
    assert.deepEqual(result.chapters.map(c => c.id), [3]);
    assert.match(result.error, /HTTP 502/);
});

test('known chapter terminates incremental refresh without requesting older pages', async () => {
    const result = await list([
        { items: [chapter(3), chapter(2)], meta: { lastPage: 5, total: 50 } },
    ], [2]);
    assert.equal(result.complete, true);
    assert.equal(result.requests.length, 1);
});

test('a first-page block remains an explicit failure instead of a successful empty list', async () => {
    const error = Object.assign(new Error('gateway'), { response: { status: 502 } });
    const result = await list([error]);
    assert.equal(result.complete, false);
    assert.deepEqual(result.chapters, []);
    assert.match(result.error, /HTTP 502/);
});

test('missing chapters cannot turn into a complete cached list', async () => {
    const result = await list([{ items: [], meta: { lastPage: 1, total: 20 } }]);
    assert.equal(result.complete, false);
    assert.match(result.error, /0 of 20/);
});

test('reader preserves image metadata and alternate hosts', async () => {
    const expected = {
        items: [{ url: 'https://images.example/one.webp', s: 1 }],
        baseUrl: 'https://images.example', alt: ['other.example'], node: 'cdn',
    };
    const payload = await new Promise((resolve, reject) => {
        vm.runInNewContext(scripts.chapter_pages, environment())(
            () => async () => async () => expected, 123, resolve, reject,
        );
    });
    assert.deepEqual(JSON.parse(payload), expected);
});

test('reader accepts bare image arrays and reports HTTP failures', async () => {
    const invoke = result => new Promise((resolve, reject) => {
        vm.runInNewContext(scripts.chapter_pages, environment())(
            () => async () => async () => {
                if (result instanceof Error) throw result;
                return result;
            }, 123, resolve, reject,
        );
    });
    assert.deepEqual(JSON.parse(await invoke([{ url: 'https://images.example/one.webp' }])), {
        items: [{ url: 'https://images.example/one.webp' }],
    });
    const error = Object.assign(new Error('gateway'), { response: { status: 502 } });
    await assert.rejects(invoke(error), reason => /HTTP 502/.test(reason));
});

test('site API discovery uses the site GET helper and its decoded response', async () => {
    const module = `
        const axios = { get: async (url, options) => ({ data: { url, options, decoded: true } }) };
        export const routes = { manga: { chapters: id => \`/manga/\${id}/chapters\` } };
        export const client = { get: async (url, options) => (await axios.get(url, options)).data };
    `;
    const context = environment();
    context.performance = { getEntriesByType: () => [{ name: 'https://comix.to/assets/main-test.js' }] };
    const siteApi = vm.runInNewContext(scripts.site_api, context, {
        importModuleDynamically: () => import(`data:text/javascript,${encodeURIComponent(module)}`),
    });
    const api = await siteApi(route => typeof route === 'function' && String(route).includes('/chapters'))();
    const result = await api('abc', { page: 2 });
    assert.equal(result.decoded, true);
    assert.equal(result.url, '/manga/abc/chapters');
    assert.equal(result.options.params.page, 2);
});
