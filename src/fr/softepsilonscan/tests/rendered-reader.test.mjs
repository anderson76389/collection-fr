import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import vm from 'node:vm';
const source = `(${await readFile(new URL('../assets/rendered_page.js', import.meta.url), 'utf8')})`;
function fixture({spinner=false, broken=false, denied=false}={}) {
    const canvas = { toDataURL() { throw new Error('Canvas is blocked.'); } };
    const plate = {scrollIntoView() {}, querySelector: selector => selector==='canvas' ? canvas : selector==='button'&&broken || selector==='.animate-spin'&&spinner ? {} : null};
    const context = {window:{}, document:{body:{innerText:denied?'Lecture impossible sur cet appareil':''}, getElementById:()=>null, querySelector:()=>null, querySelectorAll:()=>[plate,plate,plate]}};
    return {read:vm.runInNewContext(source,context),canvas,context};
}
test('locates the real rendered surface without calling the blocked canvas API',()=>{
    const env=fixture(), value=env.read(0);
    assert.equal(value.state,'ready'); assert.equal(value.element,env.canvas); assert.equal(value.total,3);
});
test('waits for a loading page',()=>assert.equal(fixture({spinner:true}).read(0).state,'waiting'));
test('does not accept a failed site page',()=>assert.equal(fixture({broken:true}).read(0).state,'error'));
test('reports device refusal instead of attributing it to Cloudflare',()=>assert.equal(fixture({denied:true}).read(0).state,'error'));
test('leaves Cloudflare to the user',()=>{const env=fixture();env.context.window._cf_chl_opt={};assert.equal(env.read(0).state,'waiting');});

test('retains the existing legacy reader fallback',()=>{const env=fixture();env.context.document.getElementById=()=>({dataset:{page:JSON.stringify({props:{reader_v2:false}})}});assert.equal(env.read(0).state,'legacy');});
