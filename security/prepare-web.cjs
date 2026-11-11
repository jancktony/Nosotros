const {writeFileSync}=require('node:fs');
const config=JSON.parse(process.env.GS||'{}');
if(config.project_info?.project_id!=='nosotros-1e43a')throw Error('Configuración de proyecto incorrecta');
const client=config.client?.find(c=>c.client_info?.android_client_info?.package_name==='com.tony.nosotros');
if(!client?.api_key?.[0]?.current_key)throw Error('Falta configuración pública de Firebase');
const web={apiKey:client.api_key[0].current_key,projectId:config.project_info.project_id,
 appId:client.client_info.mobilesdk_app_id,authDomain:config.project_info.project_id+'.firebaseapp.com'};
writeFileSync('web/config.js','window.FB='+JSON.stringify(web)+';\n');
if(!process.env.GOOGLE_APPLICATION_CREDENTIALS)throw Error('Falta ruta temporal de credencial');
const account=JSON.parse(process.env.FIREBASE_SERVICE_ACCOUNT||'{}');
if(account.project_id!==web.projectId||!account.private_key)throw Error('Cuenta de servicio incorrecta');
writeFileSync(process.env.GOOGLE_APPLICATION_CREDENTIALS,JSON.stringify(account),{mode:0o600});
console.log('Configuración pública de web generada; credencial privada guardada fuera de web.');
