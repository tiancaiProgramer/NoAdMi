import fs from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';
let checks=0;
const script=fs.readFileSync(new URL('../../src/main/assets/market-detail.js',import.meta.url),'utf8');
const selector='.rec-apps.card, .applist.card';
function check(value){checks++;assert.ok(value,`DOM check ${checks}`);}
function card(title,{heading=true,nested=false,display='',priority='',name,key,foreignRoot=false}={}){
 const attrs=new Map(),styles=new Map([['display',{value:display,priority}]]);
 const c={title,isConnected:true,valid:true,attrs,
  getAttribute:k=>attrs.get(k)??null,setAttribute:(k,v)=>attrs.set(k,v),removeAttribute:k=>attrs.delete(k),
  matches:s=>{assert.equal(s,selector);return c.valid;},
  style:{setProperty:(k,value,priority='')=>styles.set(k,{value,priority}),removeProperty:k=>styles.delete(k),getPropertyValue:k=>styles.get(k)?.value??'',getPropertyPriority:k=>styles.get(k)?.priority??''},
  querySelector:s=>{assert.equal(s,'.J_recAppsHeader .rec-apps-title');return heading?{get textContent(){return c.title;},closest:s=>{assert.equal(s,selector);return nested?{}:c;}}:null}
 };if(name)c.__vue__={$options:{name},keyName:key,$el:foreignRoot?{}:c};return c;
}
const aliases=['其他用户还喜欢','其它用户还喜欢','安装过该应用的用户还喜欢','使用该应用的用户也喜欢','People also like','You might also like','热门实用工具推荐','热门实用工具游戏','热门实用工具','热门休闲应用','热门休闲游戏','热门社交游戏','热门聊天社交游戏','更多推荐'];
const all=aliases.map(t=>card(t));
for(const name of ['recApps','AppListHorizon'])for(const type of ['detailRelatedApps','appDetailRecApps','detailSameCategoryApps','detailAlsoFavorite'])for(const suffix of ['','_0','_12'])all.push(card('服务器更换后的分类推荐标题',{name,key:type+suffix}));
const hiddenCount=all.length;
for(const title of ['同开发者应用','应用描述','评论','应用权限','其他用户还喜欢的功能描述','热门新闻说明',''])all.push(card(title));
all.push(card('其他用户还喜欢',{heading:false}),card('更多推荐',{nested:true}),card('服务器标题',{name:'other',key:'appDetailRecApps_0'}),card('服务器标题',{name:'recApps',key:'detailSameDevApps_0'}),card('服务器标题',{name:'recApps',key:'appDetailRecApps_evil'}),card('服务器标题',{name:'recApps',key:'appDetailRecApps_0',foreignRoot:true}));
const frames=[];let observer,observerCount=0,settings;
const context={window:{},document:{documentElement:{},querySelectorAll:s=>{assert.equal(s,selector);return all.filter(c=>c.valid&&c.isConnected);}},MutationObserver:class{constructor(callback){observer=callback;observerCount++;}observe(root,options){settings=options;}},requestAnimationFrame:cb=>frames.push(cb)};
vm.createContext(context);vm.runInContext(script,context);
for(let i=0;i<all.length;i++)check((all[i].style.getPropertyValue('display')==='none')===(i<hiddenCount));
check(settings.childList&&settings.subtree&&settings.characterData&&!settings.attributes);
const state=context.window.__noadmiMarketDetail;check(state.counts.cards===hiddenCount);
vm.runInContext(script,context);check(observerCount===1);check(state.counts.cards===hiddenCount);
const late=card('另一个动态推荐标题',{name:'recApps',key:'detailSameCategoryApps_7',display:'flex',priority:'important'});all.push(late);observer();observer();check(frames.length===1);frames.shift()();check(late.style.getPropertyValue('display')==='none');
late.title='隐私政策';late.__vue__.keyName='detailPrivacy_0';observer();frames.shift()();check(late.style.getPropertyValue('display')==='flex');check(late.style.getPropertyPriority('display')==='important');check(late.getAttribute('data-noadmi-hidden')===null);
all[0].title='应用权限';observer();frames.shift()();check(all[0].style.getPropertyValue('display')==='');check(all[0].getAttribute('data-noadmi-hidden')===null);
all[1].valid=false;observer();frames.shift()();check(all[1].style.getPropertyValue('display')==='');
all[2].isConnected=false;observer();frames.shift()();check(all[2].style.getPropertyValue('display')==='');
console.log(`PASS: ${checks} recommendation variants, semantic component boundaries, retained content, late rendering, idempotence, recycled cards and style restoration checks`);
