#!/usr/bin/env node
// Offline logic checks against a generated viewer. This is not browser/layout evidence.
'use strict';
const fs=require('node:fs');
const vm=require('node:vm');
const assert=require('node:assert/strict');
const page=fs.readFileSync(process.argv[2],'utf8');
const data=page.match(/<script type="application\/json" id="data">([\s\S]*?)<\/script>/)[1];
const script=page.match(/<script>\s*([\s\S]*?)<\/script>/)[1];
const payload=JSON.parse(data);

class Element{
 constructor(tag='div'){this.tag=tag;this.children=[];this._text='';this.value='';this.listeners={};}
 set textContent(text){this._text=String(text);this.children=[];}
 get textContent(){return this._text+this.children.map(n=>n.textContent).join('');}
 append(...nodes){this.children.push(...nodes);}
 replaceChildren(...nodes){this._text='';this.children=[...nodes];}
 setAttribute(key,value){this[key]=value;}
 addEventListener(event,fn){this.listeners[event]=fn;}
 click(){if(this.onclick)this.onclick();}
}

function boot(blocked=false){
 const elements={};
 for(const id of ['data','status','lang','meaning','level','flag','search','identity','cards','position','prev','next','toggle','review','decision','comment','saved','export','import','evidence'])elements[id]=new Element();
 elements.data.textContent=data;
 const storage=new Map();
 let download=null;
 const sandbox={document:{getElementById:id=>elements[id],createElement:tag=>new Element(tag),createTextNode:text=>{const n=new Element('#text');n.textContent=text;return n;}},
  localStorage:{getItem:key=>{if(blocked)throw new Error('disabled');return storage.get(key)||null;},setItem:(key,value)=>{if(blocked)throw new Error('disabled');storage.set(key,value);}},
  URL:{createObjectURL:blob=>{download=blob;return 'blob:test';},revokeObjectURL:()=>{}},Blob:Blob,setTimeout:()=>{}};
 const context=vm.createContext(sandbox);vm.runInContext(script,context);
 return {elements,context,storage,run:code=>vm.runInContext(code,context),download:()=>download};
}

async function main(){
 const app=boot(),e=app.elements;
 assert.equal(app.run('filtered.length'),payload.rows.filter(r=>r.lang==='en'&&r.meaningLang==='ru'&&r.level==='beginner').length);
 assert.ok(e.position.textContent.startsWith('1 / '));
 assert.equal(e.cards.children[1].children[1].children[1].tag,'mark');
 assert.equal(app.run('showMeaning'),false);e.toggle.onclick();assert.equal(app.run('showMeaning'),true);
 // Drafts survive navigation without inventing a verdict.
 e.comment.value='Draft before selecting a verdict';e.comment.oninput();const first=app.run('rowKey(filtered[index])');
 e.next.onclick();e.prev.onclick();assert.equal(e.comment.value,'Draft before selecting a verdict');assert.equal(e.decision.value,'unreviewed');
 e.decision.value='needs-review';e.decision.onchange();e.export.onclick();
 const saved=JSON.parse(await app.download().text());assert.equal(saved.input.archiveSha256,payload.audit.input.archiveSha256);assert.equal(saved.decisions[first].decision,'needs-review');
 // Same identity is independent of object key order; different input cannot be imported.
 saved.input={sourceVersion:saved.input.sourceVersion,previewLogicalSha256:saved.input.previewLogicalSha256,archiveSha256:saved.input.archiveSha256};
 app.context.imported=saved;assert.equal(app.run('Object.keys(validateImported(imported)).length'),1);
 saved.input.archiveSha256='f'.repeat(64);assert.throws(()=>app.run('validateImported(imported)'),/Другой входной/);
 const event={target:{files:[{size:50,text:async()=>JSON.stringify(saved)}],value:'file'}};
 await e.import.onchange(event);assert.equal(app.run('decisions[rowKey(filtered[index])].decision'),'needs-review');assert.ok(e.status.textContent.includes('Другой входной'));
 e.lang.value='';e.meaning.value='';e.level.value='';e.flag.value='';e.search.value='';app.run('filter()');assert.equal(app.run('filtered.length'),payload.rows.length);
 e.lang.value='ja';e.meaning.value='ru';e.level.value='beginner';e.flag.value='ja-single-hiragana';app.run('filter()');assert.equal(app.run('filtered.length'),5);
 assert.ok(e.cards.textContent.includes('き'));assert.ok(e.cards.children[1].children[1].textContent.includes('神よ、我が願いを聞き給え。'));
 e.search.value='no-matching-context-ffffffff';app.run('filter()');assert.equal(app.run('filtered.length'),0);assert.ok(e.review.hidden);assert.ok(e.next.disabled);
 const denied=boot(true);denied.elements.decision.value='acceptable';denied.elements.decision.onchange();denied.elements.export.onclick();const memory=JSON.parse(await denied.download().text());assert.equal(Object.keys(memory.decisions).length,1);assert.ok(denied.elements.saved.textContent.includes('памяти'));
 // Exact per-context surfaces, with UTF-16 offsets, are what gets highlighted.
 for(const r of payload.rows)for(const c of r.contexts)assert.equal(c.context.slice(c.highlight.start,c.highlight.end),c.highlight.surface);
 console.log('PASS: navigation, all-data/flag/search filters, UTF-16 highlights, drafts, persistence fallback, hash-bound export/import, rejected import preserves decisions.');
 console.log('DOM stub only; real browser layout and owner review remain open.');
}
main().catch(error=>{console.error(error);process.exitCode=1;});
