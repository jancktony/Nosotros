# Revisión · candidata privada 1.15.10

Fecha: 2026-11-21. Revisión automatizada por Codex según la guía review.
Contrato: [fiabilidad-y-viajes](../specs/fiabilidad-y-viajes.md). Implementación probada: `10430c9acd126704468a341279326f4db71e7d47`.

## Resultado

Apta para candidata privada de prueba del dueño. Se revisó el diff completo contra main: cambios de recuperación del servicio, caducidad de mediciones GPS, dos utilidades pequeñas y sus regresiones; sin dependencias nuevas, migraciones, permisos o cambios visuales nativos. No se encontraron bloqueos para este alcance. El resto del contrato es una propuesta interactiva, no implementación nativa.

## Evidencia

- [Seguridad, web y compilación Android](https://github.com/jancktony/Nosotros/actions/runs/44934474572): aprobado.
- [Voz en emulador Android](https://github.com/jancktony/Nosotros/actions/runs/44934468582): 7 pruebas aprobadas; códec, pulsación, conexión, recepción y recursos tras inactividad.
- [Mapa e instalación](https://github.com/jancktony/Nosotros/actions/runs/44934468553): 8 pruebas instrumentales aprobadas.
- [Compilación firmada y publicación privada](https://github.com/jancktony/Nosotros/actions/runs/44934468607): aprobado. Candidata privada publicada y verificada: 74.245.375 bytes en 142 fragmentos. SHA-256 del APK firmado: `ac135d39f7b4ce5472b37eb983a15b2f5ea36f667159505ef3f77222138658e6`.
- Las reglas exigen que las pruebas de voz, mapa y seguridad pertenezcan a la misma revisión antes de cargar el APK.

## Cobertura y límites

- El servicio ya no abandona después de tres fallos; espera 2, 4, 8, 16 y después 30 segundos mientras alguien espera en Voz o mantiene pulsado. No reinicia una negociación activa. Destrucción del servicio cancela el reintento y la pulsación sigue siendo necesaria para abrir el micrófono.
- La navegación distingue actividad del teléfono de `locationAt`, rechaza mediciones pausadas, futuras, ausentes o imprecisas y detiene el progreso al caducar el GPS.
- Los tests unitarios verifican caducidad y precisión, y una política de espera acotada después del tercer fallo. Las pruebas de voz existentes no reproducen todos los operadores móviles ni demuestran latencia instantánea en teléfonos físicos.
- No se ha probado esta candidata físicamente entre los dos teléfonos. STUN no elimina bloqueos NAT; el canal propio permanente continúa pendiente de infraestructura autorizada.
- Viaje compartido, respaldo con acuse de voz, aviso de batería, recuerdos y temas de la nueva propuesta permanecen simulados en la vista previa. La APK mantiene su interfaz anterior. Street View se pospuso expresamente.
- El publicador carga y verifica un manifiesto privado. No cambia `testVersion`, `relVersion` ni aprueba o libera por el dueño. No activa facturación.

La implementación se integró en main mediante [PR #24](https://github.com/jancktony/Nosotros/pull/24), commit `c092c24e1d2e8454f658815f7e0dcf07e06fcf2c`. Se conserva la decisión manual del dueño sobre prueba y liberación a la pareja.
