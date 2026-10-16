import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import vm from 'node:vm';

const source = `(${await readFile(new URL('../assets/rendered_page.js', import.meta.url), 'utf8')})`;
function fixture({ total = 4, spinner = false, dimensions = true, denied = false, broken = false, legacy = false, captureThrows = false } = {}) {
    let scrolls = 0;
    const canvas = {
        width: 1080, height: 2200,
        hasAttribute: () => dimensions,
        toDataURL: () => {
            if (captureThrows) throw new Error('capture unavailable');
            return 'data:image/png;base64,iVBORw0KGgo=';
        },
    };
    const plates = Array.from({ length: total }, () => ({
        scrollIntoView: () => { scrolls++; },
        querySelector: selector => selector === 'canvas' ? canvas : selector === 'button' && broken ? {} : selector === '.animate-spin' && spinner ? {} : null,
    }));
    const document = {
        body: { innerText: denied ? 'Lecture impossible sur cet appareil' : '' },
        getElementById: () => ({ dataset: { page: JSON.stringify({ props: { reader_v2: !legacy, page_count: 0 } }) } }),
        querySelectorAll: () => plates,
    };
    const context = vm.createContext({ window: {}, document });
    // The old implementation adds a setter to Object.prototype; this reader must not need it.
    vm.runInContext('Object.freeze(Object.prototype)', context);
    const read = vm.runInContext(source, context);
    return { read, context, get scrolls() { return scrolls; } };
}

test('uses the actual rendered page count even when page_count is zero', () => {
    const env = fixture();
    const result = env.read(2);
    assert.equal(result.state, 'page');
    assert.equal(result.index, 2);
    assert.equal(result.total, 4);
    assert.match(result.data, /^data:image\/png;base64,/);
    assert.equal(env.scrolls, 1);
});

test('a default blank canvas is not delivered as a page', () => {
    assert.equal(fixture({ dimensions: false }).read(0).state, 'waiting');
});

test('loading placeholders cannot be exported before drawing completes', () => {
    assert.equal(fixture({ spinner: true }).read(0).state, 'waiting');
});

test('an unsupported device stays an explicit error', () => {
    const result = fixture({ denied: true }).read(0);
    assert.equal(result.state, 'error');
    assert.match(result.error, /non compatible/);
});

test('a failed site image is not treated as an empty successful chapter', () => {
    assert.equal(fixture({ broken: true }).read(1).state, 'error');
});

test('legacy reader is returned to the existing implementation', () => {
    assert.equal(fixture({ legacy: true }).read(0).state, 'legacy');
});

test('capture restrictions produce a precise failure', () => {
    assert.equal(fixture({ captureThrows: true }).read(0).state, 'error');
});

test('Cloudflare must be validated by the user', () => {
    const env = fixture();
    env.context.window._cf_chl_opt = {};
    assert.match(env.read(0).error, /Cloudflare/);
});
