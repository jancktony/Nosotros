# Precisión de lugares y GPS · candidata 1.17.1

La corrección se integró en el PR #28. Revisión compilada y probada: `825a43abb2d7a45c44e9ceca9d95510c295de8dc`; merge: `165e3a267d18bbfa8ea1048bbad3f5823e1f3ab7`.

## Cambios y causa

La etiqueta anterior incluía el punto del lugar dentro de un bitmap anclado abajo: su centro estaba 8 dp por encima de la coordenada real. Ahora `place-point` dibuja el punto directamente en la coordenada del lugar; la etiqueta es independiente. Foto y halos usan alineación explícita respecto a la pantalla para conservar su centro en 2D/3D. No se mueve la foto al centro de Casa ni se modifican los lugares registrados.

`LocationQuality` rechaza lecturas inválidas, sin precisión válida, con precisión peor de 150 m, antiguas, futuras o fuera de orden según el reloj monotónico. Retiene una medición reciente de hasta 35 m durante un máximo de 60 s cuando una lectura claramente peor tiene un área de incertidumbre compatible con la misma posición. Permite desplazamientos reales y expira la preferencia para no congelar posiciones. Se conserva la edad original de la medición, sin refrescarla con un latido.

El refresco solicita muestras nuevas de alta precisión durante un máximo de 20 s, sin reutilizar la caché inicial; termina antes si obtiene precisión de 15 m o mejor. La mejor lectura nueva se publica al terminar aunque haya llegado dentro del límite habitual de envío de 10 s. Pausa, permisos, destrucción y modo en vivo cancelan la adquisición temporal. El ahorro por batería baja se mantiene incluso cuando se descarta una lectura GPS.

La navegación local usa el mismo filtro. Abrir el mapa solicita un refresco propio si la lectura es antigua o imprecisa. La ficha añade el margen de precisión reportado por el dispositivo. No se añadió sondeo periódico de alta precisión ni un servicio nuevo.

## Verificación y publicación

- Compilación debug/release y pruebas unitarias aprobadas, incluidas cuatro nuevas de calidad de medición.
- 15 pruebas instrumentales de mapa/interfaz, incluidas las propiedades del anclaje y la adquisición limitada.
- 11 pruebas instrumentales existentes de voz aprobadas.
- 32 pruebas de reglas y 9 pruebas web aprobadas; navegador en cinco anchos y cuatro temas.
- APK firmada: versión 1.17.1, código 36, **74.377.827 bytes**, **142 fragmentos**.
- SHA-256 del artefacto: `72fe7d4837a46d3a9b1d271f11d6bf3e472eedc44cc70acc997821317ceafcc3`.
- Confirmación del publicador: `2026-11-24T06:20:24.6380827Z Candidata privada 1.17.1 verificada: 74377827 bytes en 142 fragmentos. Solo el panel libera a la pareja.` La comprobación lee y verifica los bytes antes de activar ready.

Ejecuciones: seguridad/Android `45338336123`, interfaz `45338329596`, voz `45338329637`, web/reglas `45338329506`, publicación `45338329605`.

Se conserva el borrador de GitHub y la candidata privada para el administrador. No se modificaron testVersion/relVersion, no se aprobó la prueba del dueño ni se liberó a Kelin.

## Límites

Las pruebas son automáticas y con datos ficticios; falta comprobar la mejora física en los Samsung. El GPS en interiores puede seguir teniendo error. Un punto de lugar guardado en una coordenada equivocada requiere ajuste manual; no se corrige sustituyendo la posición medida. El teléfono que envía la ubicación debe actualizarse para aplicar el filtro nuevo.
