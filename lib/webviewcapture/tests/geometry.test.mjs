import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import test from 'node:test';
import vm from 'node:vm';
const source=`(${await readFile(new URL('../assets/window_geometry.js',import.meta.url),'utf8')})`;
function fixture(height=1800,width=400){
    let scroll=0;let covered=false;
    const element={contains:node=>node===element,getBoundingClientRect:()=>({top:200-scroll,left:0,right:width,width,height})};
    const window={innerHeight:1000,innerWidth:400,scrollBy:({top})=>{scroll+=top;}};
    return {cover(){covered=true;},geometry:vm.runInNewContext(source,{window,document:{elementFromPoint:()=>covered?{}:element}}),locate:()=>({state:'ready',element,total:1})};
}
test('covers a tall page fully in consecutive non-overlapping strips',()=>{
    const env=fixture();let offset=0,count=0;
    while(offset<1800){const tile=env.geometry(env.locate,0,offset);assert.equal(tile.state,'tile');assert.equal(tile.y,200);assert.ok(tile.height>0);offset+=tile.height;count++;}
    assert.equal(offset,1800);assert.equal(count,3);
});
test('preserves the final short strip',()=>{const env=fixture(1450);assert.equal(env.geometry(env.locate,0,1200).height,250);});
test('waits rather than cropping a horizontally overflowing page',()=>{const env=fixture(1800,800);assert.equal(env.geometry(env.locate,0,0).state,'waiting');});
test('passes an explicit reader error through unchanged',()=>{const env=fixture();assert.equal(env.geometry(()=>({state:'error',error:'refused'}),0,0).error,'refused');});

test('does not capture an overlay covering the reader',()=>{const env=fixture();env.cover();assert.equal(env.geometry(env.locate,0,0).state,'waiting');});
