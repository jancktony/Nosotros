# Voz y liberación de funciones (pendiente de desplegar)

El panel Administrador → Funciones ofrece tres estados: desactivada, solo el
administrador y ambos. No hace falta otro APK para cambiar estos estados una vez
instalada una versión compatible y desplegadas las reglas. No instala código
nuevo: las funciones deben estar incluidas en el APK de ambos teléfonos.

Controles: walkie en directo, notas de voz, mapa inclinado/edificios vectoriales,
fotos de perfil, widgets compactos, avisos de llegada/salida, detalles en bloqueo,
búsqueda manual de actualizaciones y actualizaciones automáticas. El panel de
versiones conserva la opción de liberar un APK concreto para la pareja.

La voz empieza desactivada. Las funciones previas conservan su estado disponible.
La opción «Solo yo» sigue a la cuenta que administra, incluso si transfiere el rol.
No desactiva autenticación, reglas de acceso, pausa de ubicación ni cerrar sesión.
Los controles no fuerzan permisos del otro teléfono ni instalación silenciosa.

## Walkie en directo

Ambos abren Voz. Uno pulsa Conectar y el otro Aceptar. Mantener pulsado transmite
PCM mono de 16 kHz por un DataChannel WebRTC cifrado; soltar detiene y libera
AudioRecord. No se crea una pista de micrófono WebRTC permanente. No se guarda
el audio en directo. Las pulsaciones duran hasta 30 segundos y la conexión hasta
5 minutos. Salir de Voz, pasar a segundo plano, bloquear la pantalla o cerrar
sesión cierra la conexión y el audio. No recibe con la pantalla bloqueada.

Firestore almacena únicamente oferta/respuesta SDP y candidatos ICE, privados a
la pareja. Se limpian los documentos conocidos al terminar. Si el proceso muere
o pierde acceso pueden quedar documentos de señalización: no contienen audio.
Los candidatos pueden revelar direcciones de red a la pareja. Se usa STUN de
Google para descubrir conectividad; el audio viaja directamente entre teléfonos.

No hay TURN configurado. Algunas redes móviles, NAT y firewalls impedirán la
conexión; se informa del fallo y se pueden usar notas. Antes de prometer conexión
en cualquier red, añadir un servicio TURN con credenciales efímeras obtenidas
desde un servidor autenticado. No guardar claves TURN permanentes en el APK.
No hay servidor nuevo ni facturación nueva habilitada por este cambio.

## Notas de voz

Mantener pulsado graba AAC/M4A mono; soltar envía. Hasta 30 segundos/150 KB.
Las notas se guardan como bytes en Firestore bajo la pareja, no con URL pública.
Se consultan las últimas 30; no hay borrado automático de notas antiguas.
El autor puede borrar las suyas y el administrador cualquiera. Se cancelan las
grabaciones al salir y se eliminan los archivos temporales de caché al acabar;
una terminación abrupta del proceso puede dejar temporales en la caché privada.
La caché persistente de Firestore conserva datos previamente descargados: retirar
una función no borra copias que el otro teléfono ya recibió.

La transmisión en directo no funciona como almacenamiento diferido: si el otro
no abre/acepta, no oye el mensaje. Las notas cubren ese caso. No hay nuevas
notificaciones push ni un servicio de micrófono en segundo plano.

## Seguridad y límites de los controles

Solo el administrador puede cambiar `featureFlags`; claves/estados desconocidos
se rechazan. Las reglas verifican el correo, pertenencia a la pareja, autor,
tamaño, duración y campos. La pareja no puede suplantar autor ni responder su
propia oferta. La voz está también bloqueada en reglas cuando se desactiva.
Las funciones locales (mapa, widgets, actualizaciones) dependen de la versión de
la app: no constituyen una revocación de acceso ni impiden usar APK antiguos.

Los cambios se reciben con conexión; se conserva la configuración local más
reciente sin red. No son un corte remoto instantáneo si el teléfono está sin
conexión a Firebase. La sesión directa tiene el límite local de cinco minutos.
Retirar la voz desmonta su pantalla/canal en clientes que reciben el cambio.
Los ajustes personales de silencio, permisos y pausa siguen teniendo prioridad.

## Activación pendiente

1. Ejecutar CI Android y pruebas de reglas.
2. Con acceso administrativo a Firebase, desplegar `firestore.rules` del repositorio
   al proyecto `nosotros-1e43a`. Este entorno no tiene credenciales de despliegue.
3. Probar en los dos Samsung: permisos concedidos/denegados, pulsar/soltar/cancelar,
   límite 30 s, reproducción/borrado, desconectar, bloquear, cambiar pestaña, salir
   de la app, cerrar sesión y retirada de funciones durante el uso. Probar entre
   Wi-Fi y datos; verificar comportamiento cuando STUN no logra conexión.
4. Fijar versión/código nuevos y publicar una compilación firmada, primero para
   el administrador. Ambos teléfonos necesitan ese APK. Esta preparación no
   incrementa la versión 1.8.1 para evitar publicación anticipada.
5. Activar notas «Solo yo» para probar y luego «Ambos». El directo requiere
   «Ambos» para que la pareja pueda aceptar; «Solo yo» permite inspeccionar la
   pantalla pero no establece conversación con un destinatario sin acceso.

Dependencia: https://github.com/webrtc-sdk/android (150.7871.04).
