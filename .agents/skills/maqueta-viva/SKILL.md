---
name: maqueta-viva
description: >-
  Convierte una lista de funcionalidades (existentes o futuras) en UNA maqueta HTML
  autocontenida, simulada e interactiva, que muestra cómo funcionaría la solución — para
  presentarla en vivo a un decisor no técnico (un cliente, un jefe, una doctora). Úsala
  SIEMPRE que el usuario diga: "maqueta simulada", "demo interactivo", "simula cómo
  funcionaría", "quiero verlo funcionando antes de construirlo", "que se vea realista",
  "demo para el cliente", "mockup que se mueva", "enséñale la potencialidad", o entregue
  funcionalidades y quiera VERLAS operando. También en inglés: "interactive demo",
  "simulated mockup", "show how it would work", "clickable prototype". NO es para mockups
  estáticos de pantallas (para eso está Claude Design / disenar-con-claude-design) ni para
  construir la funcionalidad real (eso es implementación normal).
---

# Maqueta viva — de funcionalidades a demo que se opera

## El contrato de lo que produces

Una sola página HTML autocontenida (publicada como artefacto, o archivo local si no se
puede publicar) que simula la solución funcionando de forma realista. No es un video ni un
mockup para mirar: es **teatro operable** — guion + pausas humanas + improvisación acotada
+ reglas que se defienden solas. La maqueta debe:

1. **Verse como el producto real**: marco de dispositivo fiel, marca real del cliente,
   datos verosímiles pero ficticios.
2. **Reproducirse sola como una película, PERO detenerse donde decide una persona** — el
   presentador (o el decisor mismo) hace ese clic en vivo.
3. **Aceptar órdenes escritas libres** con un mini-intérprete sobre el estado del demo, y
   responder con honestidad cuando no entiende.
4. **Demostrar los guardrails negándose en escena** a lo prohibido, con el motivo.
5. **Separar con etiquetas visibles** lo que YA funciona de lo que es simulación.
6. **No afirmar como real nada que no esté construido.**

## Entrada que debes conseguir antes de dibujar

Pregunta lo que falte — sin esto la maqueta miente o queda genérica:

- **Las funcionalidades**, y para cada una: ¿existe hoy o es futura? (decide su etiqueta).
- **A quién se le presenta** y qué le duele (el demo se ordena por SU dolor, no por la
  arquitectura).
- **La marca real**: colores, logo, tipografía — extráelos de material del cliente (un
  mockup suyo, su web, un PDF). Jamás inventes una marca; si no hay, neutra sobria y dilo.
- **Las reglas de negocio** que el demo debe respetar y mostrar (las prohibiciones valen
  más que las funciones: son el mejor argumento de venta).
- **Textos reales del sistema** (plantillas, mensajes): úsalos textuales si existen.

## Los 7 patrones probados

### 1 · Marco de dispositivo fiel (el iPhone)

Tamaño FIJO — el error clásico es el teléfono que crece con el contenido:

```css
.telefono {
  width: 344px; max-width: 100%; height: min(716px, 92vh);
  display: flex; flex-direction: column;
  background: linear-gradient(155deg,#4a4b50,#202124 30%,#17181a 55%,#33343a);
  border-radius: 58px; padding: 11px; position: relative;
  box-shadow: 0 34px 80px rgba(0,0,0,.30), inset 0 0 0 1.5px rgba(255,255,255,.16),
              inset 0 0 0 4px #0c0c0d;
}
/* ::before / ::after = botones físicos laterales (barras absolutas fuera del borde) */
.pantalla { flex:1; min-height:0; display:flex; flex-direction:column;
  background:#fff; border-radius:47px; overflow:hidden; position:relative; }
.chat { flex:1 1 0; min-height:0; overflow-y:auto; scroll-behavior:smooth;
  display:flex; flex-direction:column; gap:9px; scrollbar-width:none; }
.chat::-webkit-scrollbar { display:none; }
```

Más: Dynamic Island (píldora negra absoluta con lente `radial-gradient`), barra de estado
(hora + señal/wifi/batería en SVG propio), barra de inicio abajo. El chat corre POR DENTRO
con scroll invisible, **de arriba hacia abajo** (anclarlo abajo se siente al revés), y
`chatEl.scrollTop = chatEl.scrollHeight` tras cada burbuja y tras el "escribiendo…".

### 2 · Reproductor de conversación

Mensajes tipados: `pac` (gris, izquierda) · `bot` (color de marca suave, derecha, con
rótulo ASISTENTE) · `sys` (nota verde centrada) · `sysambar` (nota ámbar) · `doc` (burbuja
con ficha de documento PDF) · `tabla` (tarjeta estructurada). Indicador "escribiendo…"
(tres puntos animados) antes de cada mensaje del bot. Tiempos: ~1250 ms de escritura,
~1500 ms entre mensajes, `p` opcional por mensaje. **Token de cancelación**: un contador
que se incrementa al cambiar de escenario; todo `setTimeout` verifica su token antes de
pintar — sin esto los escenarios se mezclan.

En las tarjetas `tabla`, cada fila lleva una marca que ES una regla distinta:
`✓` se puede · `→` se puede pero cambia algo (reubicado) · `✗` prohibido por el dominio ·
`–` neutro. Nunca uses las marcas como decoración.

### 3 · Escenarios en pestañas

Botones A/B/C/D/E a la izquierda (o arriba en móvil), el dispositivo al lado. Un flujo por
pestaña: el feliz, el del guardrail, el del borde. Botón "↺ Volver a verlo". El primero se
autodispara con IntersectionObserver al entrar en vista.

### 4 · Pausa humana (lo que separa demo de video)

Mensajes-acción con botones que DETIENEN la reproducción: `{ q:'accion', tsi:'Confirmar',
tadj:'Ajustar algo', tno:'Cancelar', si:[...], adj:[...], no:[...] }`. El bucle se frena al
pintarlos y continúa por la rama elegida (las ramas pueden anidar otra acción). En la
reunión, ese clic lo da el presentador — o mejor, el decisor. Al avanzar, desactiva los
botones viejos y marca el elegido.

### 5 · Compositor libre (órdenes escritas)

Campo de texto + botón enviar dentro de la pantalla + chips de sugerencia (que también se
pueden tocar). Un mini-intérprete por intenciones sobre un estado re-renderizable:

```js
function nrm(t){ return t.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g,''); }
// familias de intención por regex sobre nrm(texto): cancelar, aplicar, explicar-porqué,
// orden-prohibida, persona+hora (/(\d{1,2})[:h.](\d{2})/ y /a las (\d{1,2})/), etc.
// Cada intención muta `plan` y re-renderiza la tarjeta. Orden de evaluación: de la más
// específica a la más general.
```

**Fallback honesto obligatorio**: si no reconoce la orden → *"En la aplicación real
entendería cualquier orden. En esta demostración reconozco, por ejemplo: «…», «…»"* —
nunca finjas entender.

### 6 · Guardrails en escena

Incluye siempre al menos una orden prohibida entre los chips (p. ej. asignar a quien no
puede). El agente **se niega con el motivo**: *"esa regla vive en el código — no se salta
ni con una orden directa"*. Ver al sistema negarse convence más que cualquier párrafo de
seguridad. Si el decisor mismo la escribe, mejor.

### 7 · Autonomía por niveles

Cuando el demo incluye a un jefe dando órdenes: el agente **aplica solo lo que no
perjudica a nadie** (marcado APLICADO, firmado, con «deshacer» cuyo uso *también* queda en
la auditoría — deshacer nunca borra el rastro) y **consulta lo que le cambia algo a una
persona**. La frontera de la autonomía no es técnica, es humana. Esa frase vende.

## Reglas de honestidad (innegociables)

- Etiquetas visibles: **EN OPERACIÓN** (verde) para lo construido · **DEMOSTRACIÓN · ASÍ
  FUNCIONARÁ** (ámbar) para lo simulado. Nunca mezclarlas.
- Nota al pie del dispositivo: *"Simulación: fechas y personas ficticias; las reglas y el
  tono, los reales."*
- Todo contador que se muestre sale de datos visibles en la misma maqueta.
- Si el sistema real tiene plantillas/mensajes, cópialos textuales — no los "mejores".
- Nombres de personas SIEMPRE ficticios; teléfonos y cédulas enmascarados (`3•• ••• 88 30`).

## Reglas de forma

- Marca real del cliente o neutra sobria. Presupuesto de color: **un** botón sólido
  protagonista por vista; el resto, contorno. Sin gradientes decorativos, sin emoji fuera
  de los textos reales del sistema, sin barras de progreso inventadas.
- `prefers-reduced-motion`: pintar todo de una, sin timers.
- El idioma y el trato (usted/tú) del cliente, no los tuyos.

## Proceso de construcción

1. Fuente `*-src.html` con placeholders `__LOGO__`/`__FOTO__`; imágenes reales en base64
   (bajadas de peso: < 70 KB cada una) inyectadas por un script de build.
2. **Validar SIEMPRE con node antes de publicar**: (a) `new Function(js)` sobre el script
   extraído — sintaxis; (b) probar las regex de intención contra las frases exactas de los
   chips — las N intenciones deben clasificar bien; (c) verificar que los ids del wiring
   existen en el HTML.
3. Publicar como artefacto; iterar re-publicando al MISMO path (misma URL).
4. Al presentar cambios al usuario, decirle qué escenario probar y qué va a ver.

## Trampas conocidas (aprendidas a golpes)

- **Heredocs largos de bash se truncan** en silencio → los scripts de edición van a un
  archivo `.py` y se ejecutan; nunca inline si superan ~80 líneas.
- **Saltos de línea reales dentro de strings JS** rompen la sintaxis → siempre `\n`
  escapado (en python, construirlo con `chr(92)+'n'`).
- El teléfono que crece con el contenido delata la maqueta → alto fijo + scroll interno.
- El chat anclado abajo "se siente al revés" → de arriba hacia abajo siempre.
- Botones de acciones viejas deben desactivarse cuando aparece un plan nuevo.
- Contadores inventados: si el texto dice "6 sin leer" y en pantalla hay 3, el decisor lo
  nota. Cuenta lo que se ve.
