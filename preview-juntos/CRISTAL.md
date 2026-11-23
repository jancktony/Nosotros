# Cristal · propuesta 05

Modo: operar. Vista previa interactiva para revisar antes de cambiar la app.

Referencia: capturas de Apple Maps y Encontrar proporcionadas por Jancktony. Se adopta la composición de paneles flotantes, búsqueda, accesos a lugares y resumen de navegación; no se copian recursos de Apple.

El rosa original de Nosotros es el tema predeterminado. General permite elegir temas completos y probar acentos opcionales para la ruta. Los tokens de la paleta nocturna anterior se conservan sin modificarlos.

El mapa usa ubicaciones de ejemplo, con dibujo de respaldo cuando no carga el proveedor. La ruta, sus instrucciones, llegada, parada y actividad son simulaciones identificadas. No usa GPS ni transmite voz, fotos, comandos o cambios de versiones a los teléfonos. La autenticación privada del dueño se conserva.

Guías aplicadas: Impeccable (jerarquía, controles adaptables y revisión visual) y Maqueta viva (interacciones simuladas y límites visibles). El cristal está solicitado expresamente por el dueño. Se revisan las nueve pantallas en Chromium a 320, 390, 430, 1024 y 1440 px, gestos, captura del micrófono, rutas y conservación de colores. Estas pruebas no acreditan rendimiento nativo ni funcionamiento del walkie real.

Canal de revisión: https://nosotros-1e43a--juntos-9azq8mkc.web.app/rediseno.html?v=mejoras1

Solo se publica esta propuesta en el canal temporal existente. Integrarla en Android o en la web principal requiere el visto bueno del diseño; liberar a Kelin sigue siendo una decisión manual del dueño.

Corrección de paleta solicitada: el tema predeterminado es Rosa original con los valores de Palettes.Pink (`#F9F0F3`, `#A95476`, `#3B0A24`). Rojo y negro, Negro y rosa y Rosa completo son alternativas individuales. La selección se guarda únicamente en este navegador; no modifica la apariencia ni los permisos de la pareja. Los acentos no sustituyen el selector de temas.

Ajuste tras revisión del dueño: Rojo y negro usa rojo intenso `#E6002E` en micrófono, navegación seleccionada, botones y etiqueta Juntos; iconos `#FF304F` sobre paneles negros. El rosa original predeterminado se conserva.

Corrección de Más: todos los iconos, el corazón y las tarjetas usan el tema seleccionado, eliminando los acentos menta, lavanda y azul heredados por posición.

Nueva propuesta: Más → Mejoras en prueba. Diagnóstico/nota de voz con acuse simulado, viaje voluntario cancelable, cinco estados GPS, batería optativa interna, favoritos y revisión del dueño. Añadir o modificar observaciones invalida la aprobación de ejemplo; liberar requiere una decisión explícita y solo se simula. Ninguna de estas demostraciones lee o escribe datos de la pareja.

23-11-2026 · Propuesta de direcciones: Lugares → Añadir lugar permite escribir ciudad y dirección, revisar un punto ilustrativo y mostrar la dirección como título en lista/mapa. Las entradas se insertan como texto, nunca HTML; editar invalida la revisión. No consulta geocodificadores ni verifica coordenadas: el mapa utiliza un punto fijo de ejemplo y lo indica. Conserva lugares anteriores sin dirección en la demostración. Ninguna escritura en Firebase ni cambios en Android/web principal. Pendiente del visto bueno del dueño antes de integrar dirección separada, búsqueda y ajuste del pin reales.

23-11-2026 · Actividad compacta: franja de 34 px, icono de Instagram de 22 px servido localmente, nombre y antigüedad en una línea. Conserva título accesible «última aplicación detectada» y etiqueta de ejemplo; no implica uso actual confirmado. Pantalla de inicio mantiene un icono genérico de casa, identificado como tal. Android aún no comparte iconos; esta es la propuesta visual anterior a la integración. Paletas, dirección y acceso privado se conservan.

23-11-2026 · Batería inspirada en iOS: silueta horizontal SVG, relleno proporcional y porcentaje dentro; texto con contraste diferente en parte llena/vacía. Conserva la paleta personal y una etiqueta accesible con persona, porcentaje y condición de ejemplo. Cambiar de perfil actualiza ambos. Sin lecturas de batería reales ni cambios en Android.

23-11-2026 · Tres barras inspiradas en la referencia pública de Code XR: Cristal (selección en píldora), Flotante (selección elevada) y Relieve (selección rectangular con profundidad). Ajustes → General permite elegir estilo y deslizamiento, rebote suave, fundido o sin animación. Guarda únicamente preferencias del navegador y respeta reduced-motion/reduced-transparency. El rebote es optativo por solicitud de animaciones intercambiables; se conserva el deslizamiento como predeterminado pese a la advertencia estética genérica del detector. Cuatro accesos originales, mismas dimensiones exteriores e independencia del mapa; escritorio conserva su navegación lateral existente. La ficha, direcciones, actividad compacta y batería de propuestas anteriores se conservan. No se ha actualizado la APK ni la web principal.

23-11-2026 · Barra más pequeña por petición del dueño: ancho máximo 320 px centrado, margen lateral adaptable, altura aproximada de 54–56 px e iconos de 18 px. Conserva botones táctiles de al menos 44 px, los cuatro textos, estilos y animaciones. Solo cambia el componente de navegación; el mapa conserva su ancho.

23-11-2026 · Revisión con TinyFish, ejecución e76d853a-e5cf-42b3-8759-d791c971f9aa: el agente informó reproducción del reel de Facebook 1169089658979277 y un video público en TikTok; la demo del autor estaba pausada. Reportó indicador luminoso que se desplaza/deforma, cambio de brillo, ligera elevación de iconos y hover; estimó 200–400 ms sin medición cuadro a cuadro. No entregó capturas de la reproducción y sus variantes se describieron con incertidumbre, por lo que no se afirma igualdad exacta con el reel. Se añade a la propuesta núcleo que se estira al cambiar de pestaña, reflejo breve y elevación del icono, en la paleta personal y sin ampliar la barra. Se cancelan efectos anteriores al cambiar rápido o activar movimiento reducido; sin animaciones continuas, nuevas lecturas privadas ni compras del código del autor.

23-11-2026 · Corrección tras rechazo de la aproximación: se obtuvo temporalmente el MP4 público del reel 1169089658979277 con Chromium y se descargó por IPv4; la primera respuesta era solo el segmento de inicialización. Archivo completo: 720×1280, 5,566667 s. Fotogramas examinados con ffmpeg a 4 fps y recorte de las barras a 3 s. La inspección directa contradice el informe genérico inicial de TinyFish: las tres primeras variantes son un contorno en onda sobre la línea inferior, un foco trapezoidal desde el borde superior y una muesca real que sigue un círculo elevado; se ve una cuarta variante líquida aparte, fuera de las tres pedidas. El segundo intento de TinyFish no pudo reproducirlo, por lo que no se usó como evidencia visual.

La nueva propuesta conserva las claves de preferencia antiguas, pero las opciones se presentan como Onda, Foco y Muesca. El contorno SVG y el recorte de la superficie siguen la posición animada del indicador; no se aplica ya el brillo/deformación genéricos de la propuesta rechazada. Se conserva la barra compacta y la paleta individual. «Probar en vista móvil» muestra explícitamente el componente, también desde escritorio. MP4, fotogramas y URLs temporales se guardaron solo en /tmp para análisis y no se publican ni incorporan al repositorio. Sigue pendiente de aprobación del dueño para integrar en la app.
