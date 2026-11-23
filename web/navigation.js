/* Private panel navigation; preferences stay in this browser, no Firebase writes. */
(()=>{
 const styles={wave:'Onda',spotlight:'Foco',notch:'Muesca'},motions={slide:'Deslizamiento',spring:'Rebote suave',fade:'Fundido',none:'Sin animación'};
 const read=(key,choices,fallback)=>{try{const v=localStorage.getItem(key);return Object.hasOwn(choices,v)?v:fallback}catch{return fallback}};
 let style=read('nosotros-dock-style',styles,'wave'),motion=read('nosotros-dock-motion',motions,'slide'),nav=null,previous=0,frame=0,fade=null;
 const reduced=()=>typeof matchMedia==='function'&&matchMedia('(prefers-reduced-motion: reduce)').matches;
 const icons={phones:'<path d="m3 5 6-2 6 2 6-2v16l-6 2-6-2-6 2ZM9 3v16M15 5v16"/>',photos:'<rect x="3" y="4" width="18" height="16" rx="3"/><circle cx="8" cy="9" r="1"/><path d="m4 18 5-5 3 3 4-6 5 7"/>',settings:'<path d="m9 3 1-1h4l1 3 3 1 3-1 2 4-2 2v3l2 2-2 4-3-1-3 1-1 3h-4l-1-3-3-1-3 1-2-4 2-2v-3L2 9l2-4 3 1Z"/><circle cx="12" cy="12" r="3"/>',admin:'<path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6Z"/>'};
 const labels={phones:'Teléfonos',photos:'Fotos',settings:'Ajustes',admin:'Admin'};
 function geometry(){
  if(!nav?.isConnected)return;
  const w=nav.clientWidth,h=nav.clientHeight,box=nav.getBoundingClientRect(),marker=nav.querySelector('.dock-selection'),m=marker.getBoundingClientRect(),x=m.left-box.left+m.width/2;
  const svg=nav.querySelector('.dock-contour');svg.setAttribute('viewBox','0 0 '+w+' '+h);
  const l=Math.max(2,x-34),r=Math.min(w-2,x+34),y=h-2;
  svg.querySelector('.dock-wave').setAttribute('d',`M1 ${y} H${l} C${(l+x)/2} ${y} ${(l+x)/2} 5 ${x} 5 C${(r+x)/2} 5 ${(r+x)/2} ${y} ${r} ${y} H${w-1}`);
  const a=Math.max(21,x-34),b=Math.min(w-21,x+34);
  const d=`M24 1 H${a} C${(a+x)/2} 1 ${(a+x)/2} 26 ${x} 26 C${(b+x)/2} 26 ${(b+x)/2} 1 ${b} 1 H${w-24} Q${w-1} 1 ${w-1} 25 V${h-25} Q${w-1} ${h-1} ${w-24} ${h-1} H24 Q1 ${h-1} 1 ${h-25} V25 Q1 1 24 1 Z`;
  svg.querySelector('.dock-notch').setAttribute('d',d);nav.style.setProperty('--dock-clip',`path('${d}')`);
 }
 function animate(){
  cancelAnimationFrame(frame);fade?.cancel();fade=null;if(!nav)return;
  const moving=motion!=='none'&&!reduced(),end=performance.now()+(moving?600:0);
  if(moving&&motion==='fade')fade=nav.querySelector('.dock-selection').animate?.([{opacity:0},{opacity:1}],{duration:200});
  const draw=()=>{geometry();if(performance.now()<end)frame=requestAnimationFrame(draw)};frame=requestAnimationFrame(draw);
 }
 const resize=typeof ResizeObserver==='function'?new ResizeObserver(geometry):null;
 function enhance(){
  const found=document.querySelector('.web-nav');
  if(found===nav)return;
  resize?.disconnect();nav=found;
  if(!nav){cancelAnimationFrame(frame);fade?.cancel();previous=0;return}
  const buttons=[...nav.querySelectorAll('button')],index=Math.max(0,buttons.findIndex(b=>b.classList.contains('active')));
  nav.dataset.dockStyle=style;nav.dataset.dockMotion=motion;nav.style.setProperty('--dock-count',buttons.length);
  nav.style.setProperty('--dock-index',Math.min(previous,buttons.length-1));
  buttons.forEach((b,i)=>{const key=b.dataset.mode;b.innerHTML='<svg viewBox="0 0 24 24" aria-hidden="true">'+(icons[key]||icons.phones)+'</svg><span>'+(labels[key]||key)+'</span>';if(i===index)b.setAttribute('aria-current','page')});
  const svg=document.createElementNS('http://www.w3.org/2000/svg','svg');svg.classList.add('dock-contour');svg.setAttribute('aria-hidden','true');svg.innerHTML='<path class="dock-wave"/><path class="dock-notch"/>';nav.prepend(svg);
  const marker=document.createElement('i');marker.className='dock-selection';marker.setAttribute('aria-hidden','true');marker.innerHTML='<i class="dock-core"></i>';nav.prepend(marker);
  nav.getBoundingClientRect();nav.style.setProperty('--dock-index',index);previous=index;resize?.observe(nav);animate();
 }
 function sync(){
  if(nav){nav.dataset.dockStyle=style;nav.dataset.dockMotion=motion;animate()}
  document.querySelectorAll('[data-dock-style]').forEach(b=>{if(b.tagName==='BUTTON')b.setAttribute('aria-pressed',String(b.dataset.dockStyle===style))});
 }
 document.addEventListener('click',e=>{
  const b=e.target.closest('button[data-dock-style]');if(!b||!Object.hasOwn(styles,b.dataset.dockStyle))return;
  style=b.dataset.dockStyle;try{localStorage.setItem('nosotros-dock-style',style)}catch{}sync();
 });
 document.addEventListener('change',e=>{
  if(e.target.id!=='dock-motion'||!Object.hasOwn(motions,e.target.value))return;
  motion=e.target.value;try{localStorage.setItem('nosotros-dock-motion',motion)}catch{}sync();
 });
 if(typeof matchMedia==='function')matchMedia('(prefers-reduced-motion: reduce)').addEventListener?.('change',()=>animate());
 const app=document.getElementById('app');if(app)new MutationObserver(enhance).observe(app,{childList:true,subtree:true});
 window.NosotrosDock={settingsHTML:()=>'<section class="card dock-settings"><h2>Barra de navegación</h2><div class="dock-options">'+Object.entries(styles).map(([id,title])=>'<button data-dock-style="'+id+'" aria-pressed="'+(style===id)+'">'+title+'</button>').join('')+'</div><label for="dock-motion">Animación</label><select id="dock-motion">'+Object.entries(motions).map(([id,title])=>'<option value="'+id+'" '+(motion===id?'selected':'')+'>'+title+'</option>').join('')+'</select><p class="sub">Se guarda en este navegador y respeta el movimiento reducido del sistema. Cada teléfono conserva su propia elección.</p></section>'};
 enhance();
})();
