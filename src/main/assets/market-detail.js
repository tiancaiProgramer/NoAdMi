(function(){
'use strict';
var selector='.rec-apps.card, .applist.card';
if(window.__noadmiMarketDetail){window.__noadmiMarketDetail.apply();return;}
var state={counts:{cards:0}},queued=false,originals=new Map();
function recommendation(title,card){
 title=title.replace(/\s+/g,'').trim();if(!title)return false;
 var component=card.__vue__,name=component&&component.$options&&component.$options.name;
 var key=component&&typeof component.keyName==='string'?component.keyName.replace(/_[0-9]+$/,''):'';
 // Match the page's recommendation component, not a category-specific marketing title.
 if(component&&component.$el===card&&(name==='recApps'||name==='AppListHorizon')&&
    ['detailRelatedApps','appDetailRecApps','detailSameCategoryApps','detailAlsoFavorite'].indexOf(key)>=0)return true;
 // Bounded fallback for older resources without a component attachment.
 return ['其他用户还喜欢','其它用户还喜欢','安装过该应用的用户还喜欢','Peoplealsolike','Youmightalsolike',
  '热门实用工具推荐','热门实用工具游戏','热门实用工具','热门休闲应用','热门休闲游戏','热门社交游戏','热门聊天社交游戏','更多推荐'].indexOf(title)>=0||/(?:用户还喜欢|用户也喜欢)$/.test(title);
}
function restore(card){
 var original=originals.get(card);if(!original)return;
 if(original.value)card.style.setProperty('display',original.value,original.priority);else card.style.removeProperty('display');
 card.removeAttribute('data-noadmi-hidden');originals.delete(card);
}
function apply(){
 queued=false;
 originals.forEach(function(value,card){if(!card.isConnected||!card.matches(selector))restore(card);});
 document.querySelectorAll(selector).forEach(function(card){
  var heading=card.querySelector('.J_recAppsHeader .rec-apps-title');
  if(!heading||heading.closest(selector)!==card){restore(card);return;}
  if(!recommendation(heading.textContent||'',card)){restore(card);return;}
  if(!originals.has(card))originals.set(card,{value:card.style.getPropertyValue('display'),priority:card.style.getPropertyPriority('display')});
  if(card.getAttribute('data-noadmi-hidden')!=='recommend'){card.setAttribute('data-noadmi-hidden','recommend');state.counts.cards++;}
  card.style.setProperty('display','none','important');
 });
}
function schedule(){if(!queued){queued=true;requestAnimationFrame(apply);}}
state.apply=apply;window.__noadmiMarketDetail=state;apply();
new MutationObserver(schedule).observe(document.documentElement,{childList:true,subtree:true,characterData:true});
})();
