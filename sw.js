/* Service worker Tadabbur Harian: membuat halaman aplikasi bisa dibuka tanpa internet.
   - Halaman utama: ambil dari jaringan dulu (supaya pembaruan langsung terlihat), cadangan dari cache saat offline.
   - File statis, font, dan skrip Firebase: disimpan di cache setelah pertama kali dimuat.
   - Data ayat (API), audio, dan tajwid tidak diintersep; data ayat sudah disimpan aplikasi di perangkat. */
var V='tadabbur-v1';
var SHELL=['./','index.html','manifest.json','icon-192.png','icon-180.png'];
function route(u,req){
  var url=new URL(u,'https://x.invalid/');
  if(req.method!=='GET')return 'skip';
  if(req.headers&&req.headers.has&&req.headers.has('range'))return 'skip';
  if(req.mode==='navigate')return 'nav';
  if(url.origin===self.location.origin)return 'swr';
  if(url.hostname==='fonts.googleapis.com')return 'swr';
  if(url.hostname==='fonts.gstatic.com')return 'cache';
  if(url.hostname==='www.gstatic.com'&&url.pathname.indexOf('/firebasejs/')===0)return 'cache';
  return 'skip';
}
if(typeof self!=='undefined'&&self.addEventListener&&typeof caches!=='undefined'){
  self.addEventListener('install',function(e){
    e.waitUntil(caches.open(V).then(function(c){return Promise.all(SHELL.map(function(f){return c.add(f).catch(function(){})}))}).then(function(){return self.skipWaiting()}));
  });
  self.addEventListener('activate',function(e){
    e.waitUntil(caches.keys().then(function(ks){return Promise.all(ks.filter(function(k){return k!==V}).map(function(k){return caches.delete(k)}))}).then(function(){return self.clients.claim()}));
  });
  self.addEventListener('fetch',function(e){
    var req=e.request,r=route(req.url,req);
    if(r==='skip')return;
    var home=new URL('index.html',self.registration.scope).href;
    if(r==='nav'){
      e.respondWith(fetch(req).then(function(res){if(res&&res.ok){var cp=res.clone();caches.open(V).then(function(c){c.put(home,cp)})}return res})
        .catch(function(){return caches.match(home).then(function(h){return h||caches.match(self.registration.scope)})}));
    }else if(r==='cache'){
      e.respondWith(caches.match(req).then(function(hit){return hit||fetch(req).then(function(res){if(res&&(res.ok||res.type==='opaque')){var cp=res.clone();caches.open(V).then(function(c){c.put(req,cp)})}return res})}));
    }else{
      e.respondWith(caches.match(req).then(function(hit){
        var net=fetch(req).then(function(res){if(res&&(res.ok||res.type==='opaque')){var cp=res.clone();caches.open(V).then(function(c){c.put(req,cp)})}return res}).catch(function(){return hit});
        return hit||net;
      }));
    }
  });
}
if(typeof module!=='undefined')module.exports={route:route};
