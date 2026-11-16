window.addEventListener('load',()=>{
 const $=id=>document.getElementById(id),total=74.2,speed=.8;
 const number=n=>new Intl.NumberFormat('es-CO',{maximumFractionDigits:1}).format(n);
 let phase='downloading',percent=37,full=true,acknowledged=false,timer=null,toastTimer=null;
 const states={
 ready:{title:'Actualización disponible',copy:'La versión de ejemplo está lista para este teléfono.',label:'Lista para descargar',symbol:'↓',detail:'Archivo de ejemplo · 74,2 MB',primary:'Descargar e instalar',secondary:'Más tarde',step:-1},
 downloading:{title:'Descargando actualización',copy:'Puedes seguir usando Nosotros mientras se descarga.',label:'Descarga en curso',symbol:'↓',detail:'',primary:'Ver mapa',secondary:'Cancelar descarga',step:0},
 verifying:{title:'Comprobando archivo',copy:'Verificando la actualización antes de instalarla.',label:'Verificación en curso',symbol:'◌',detail:'Descarga completa · 74,2 MB',primary:'Ver mapa',step:1,busy:true},
 preparing:{title:'Preparando instalación',copy:'Dejando la actualización lista para Android.',label:'Preparando actualización',symbol:'◌',detail:'Archivo verificado',primary:'Ver mapa',step:2,busy:true},
 confirmation:{title:'Confirma la instalación',copy:'Android necesita tu confirmación para continuar.',label:'Esperando tu confirmación',symbol:'↗',detail:'La confirmación real pertenece a Android',primary:'Continuar instalación',step:2},
 installing:{title:'Instalando actualización',copy:'Android está aplicando la actualización.',label:'Instalación en curso',symbol:'◌',detail:'Esperando el resultado de Android',primary:'Ver mapa',step:2,busy:true},
 done:{title:'Actualización instalada',copy:'Nosotros 1.15.5 está lista para usar.',label:'Instalación confirmada',symbol:'✓',detail:'Versión 1.15.5 · ejemplo',primary:'Abrir Nosotros',step:3,done:true},
 offline:{title:'Descarga interrumpida',copy:'Revisa tu conexión y vuelve a intentarlo.',label:'La descarga no se completó',symbol:'!',detail:'Puedes reintentar desde aquí',primary:'Reintentar descarga',secondary:'Más tarde',step:0,error:true},
 invalid:{title:'No se pudo verificar',copy:'Vuelve a descargar el archivo de actualización.',label:'La comprobación no se completó',symbol:'!',detail:'No se continúa a la instalación',primary:'Descargar de nuevo',secondary:'Más tarde',step:1,error:true}
 };
 function stop(){clearInterval(timer);clearTimeout(timer);timer=null}
 function toast(text){clearTimeout(toastTimer);$('toast').textContent=text;$('toast').hidden=false;toastTimer=setTimeout(()=>{$('toast').hidden=true},2500)}
 function setPhase(value){phase=value;acknowledged=false;$('update-stage').value=value;if(['verifying','preparing','confirmation','installing','done','invalid'].includes(value))percent=100;if(value==='ready')percent=0;render()}
 function render(){
  const s=states[phase],download=phase==='downloading',error=s.error===true;
  const active=phase!=='ready'&&!acknowledged;
  $('update-view').hidden=!full;$('update-view').className='update-view'+(s.busy?' busy':'')+(error?' problem':'')+(s.done?' complete':'');
  $('update-title').textContent=s.title;$('update-copy').textContent=s.copy;
  $('progress-block').hidden=!download;$('state-block').hidden=download;
  $('big-progress').replaceChildren(document.createTextNode(Math.round(percent)));
  const pct=document.createElement('span');pct.textContent='%';$('big-progress').append(pct);
  $('download-fill').style.width=percent+'%';$('download-track').setAttribute('aria-valuenow',String(Math.round(percent)));
  $('download-bytes').textContent=number(total*percent/100)+' de '+number(total)+' MB';$('download-speed').textContent=number(speed)+' MB/s';
  const seconds=Math.ceil(total*(1-percent/100)/speed);
  $('download-eta').textContent=percent===0?'Calculando tiempo restante…':percent>=100?'Descarga completa':seconds>=60?'≈ '+Math.ceil(seconds/60)+' min restantes':'≈ '+seconds+' s restantes';
  $('state-symbol').textContent=s.symbol;$('state-label').textContent=s.label;$('state-detail').textContent=s.detail;
  $('update-primary').textContent=s.primary;$('update-secondary').hidden=!s.secondary;$('update-secondary').textContent=s.secondary||'';
  $('update-status').textContent=s.label;
  for(const [index,id] of ['step-download','step-verify','step-install'].entries()){
   const el=$(id),finished=s.done||index<s.step,current=index===s.step;
   el.className=finished?'finished':current?(error?'error':'active'):'';
   el.querySelector('span').textContent=finished?'✓':String(index+1);
   if(current)el.setAttribute('aria-current','step');else el.removeAttribute('aria-current');
  }
  $('update-percent').value=String(percent);$('side-percent').textContent=Math.round(percent)+' %';$('percent-field').hidden=!download;
  $('update-mini').hidden=full||!active;$('update-mini').className='update-mini'+(error?' problem':'');
  $('scene').classList.toggle('update-mini-visible',!full&&active);
  $('mini-icon').textContent=s.symbol;$('mini-title').textContent=s.title;
  $('mini-caption').textContent=download?number(total*percent/100)+' de '+number(total)+' MB':s.label;
  $('mini-percent').textContent=download?Math.round(percent)+' %':s.done?'✓':'';
  $('mini-fill').style.width=percent+'%';document.querySelector('.mini-track').hidden=!download;
 }
 function open(){full=true;acknowledged=false;render()}
 function minimize(map=false){full=false;if(map)$('back-map').click();render()}
 function begin(){
  stop();percent=0;setPhase('downloading');open();
  timer=setInterval(()=>{
   percent=Math.min(100,percent+3);render();
   if(percent===100){
    stop();setPhase('verifying');
    timer=setTimeout(()=>{setPhase('preparing');timer=setTimeout(()=>{timer=null;setPhase('confirmation')},1000)},1300);
   }
  },200);
 }
 $('demo-play').onclick=begin;$('open-progress').onclick=open;$('show-map').onclick=()=>minimize(true);
 $('progress-back').onclick=()=>minimize();$('update-mini').onclick=open;
 $('update-stage').onchange=()=>{stop();const v=$('update-stage').value;if(v==='downloading'&&percent===100)percent=37;setPhase(v);open()};
 $('update-percent').oninput=()=>{stop();percent=Number($('update-percent').value);render()};
 $('update-primary').onclick=()=>{
  if(['ready','offline','invalid'].includes(phase)){begin();return}
  if(phase==='confirmation'){
   stop();setPhase('installing');timer=setTimeout(()=>{timer=null;setPhase('done')},2200);return
  }
  if(phase==='done'){acknowledged=true;minimize(true);toast('Actualización instalada · ejemplo 1.15.5');return}
  minimize(true);
 };
 $('update-secondary').onclick=()=>{
  const canceled=phase==='downloading';stop();setPhase('ready');minimize(true);
  if(canceled)toast('Descarga cancelada · ejemplo');
 };
 $('settings').onclick=open;
 if($('expand').getAttribute('aria-expanded')==='true')$('expand').click();
 render();
});
