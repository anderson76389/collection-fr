import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import vm from 'node:vm';
const source = `(${await readFile(new URL('../assets/visible_page.js',import.meta.url),'utf8')})`;
function fixture({total=3,current=2,loaded=true,longstrip=false,drawn=true}={}) {
    let changes=0;
    const image={complete:loaded,src:"blob:current",naturalWidth:loaded?1200:0};
    const select={dataset:{},options:Array(total),selectedIndex:current,dispatchEvent(){changes++;}};
    const pages=Array.from({length:total},(_,i)=>({id:`d-img-${i}`,scrollIntoView(){},getBoundingClientRect:()=>({width:400,height:2000}),querySelector:()=>null,querySelectorAll:()=>drawn?[{canvas:{width:1200,height:2000}}]:[]}));
    const full={getBoundingClientRect:()=>({width:longstrip?400:0}),querySelectorAll:()=>pages};
    const single={getBoundingClientRect:()=>({width:longstrip?0:400}),querySelector:()=>image};
    const context={window:{},getComputedStyle:()=>({visibility:'visible'}),Event:class {},document:{getElementById:id=>id==='pages'?select:full,querySelector:()=>single}};
    return {read:vm.runInNewContext(source,context),select,image,context,get changes(){return changes;}};
}
test('uses the displayed reader mode, not a manga/manhwa URL guess',()=>{const env=fixture({longstrip:true});assert.equal(env.read(1).total,3);assert.equal(env.changes,0);});
test('selects a page through the ordinary reader control',()=>{const env=fixture();assert.equal(env.read(0).state,'waiting');assert.equal(env.select.selectedIndex,0);assert.equal(env.changes,1);});
test('a single-page chapter is accepted without navigating',()=>{const env=fixture({total:1,current:0});assert.equal(env.read(0).element,env.image);assert.equal(env.changes,0);});
test('does not import an unloaded paginated image',()=>assert.equal(fixture({current:0,loaded:false}).read(0).state,'waiting'));
test('does not import an empty long-strip placeholder',()=>assert.equal(fixture({longstrip:true,drawn:false}).read(0).state,'waiting'));
test('Cloudflare is not auto-solved',()=>{const env=fixture();env.context.window._cf_chl_opt={};assert.equal(env.read(0).state,'waiting');});

test('waits for the newly selected image instead of duplicating the previous page',()=>{const env=fixture();env.read(0);assert.equal(env.read(0).state,'waiting');env.image.src='blob:new';assert.equal(env.read(0).state,'ready');});
