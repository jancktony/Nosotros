---
name: deuda-invisible
description: >-
  Audita un proyecto buscando la deuda que no detectan el typecheck, el lint,
  los tests ni una revisión de código normal, porque el código compila, corre y
  no falla: simplemente no hace lo que promete. Encuentra circuitos completos
  que ninguna pantalla lee, valores constantes disfrazados de dato, columnas que
  nadie escribe o nadie muestra, métricas que ponderan un ranking y nunca se
  calculan, dos señales que son el mismo hecho, condiciones de estado que se
  quedan cortas en el tiempo, comentarios que mienten y estados terminales sin
  salida. Usa esta skill SIEMPRE que el usuario diga cosas como "audita qué
  construimos y no se usa", "qué hay a medias", "qué promete el código y no
  cumple", "busca código muerto", "por qué esto no se ve", "qué datos guardamos
  y no mostramos", "revisa si las señales sirven de algo", "esto está conectado
  de verdad?", "el badge sale siempre igual", "qué campos del esquema no toca
  nadie", "el ranking ordena algo?", "quedó algo a medio conectar", "revisa si
  la feature llegó a la UI", "hay pantallas que prometen datos que no llegan".
  Actívala también cuando el usuario sospeche que una funcionalidad "está pero
  no se nota", aunque no use la palabra auditoría.
---

# Deuda invisible

## Qué buscas

Hay una familia de defectos que ninguna herramienta automática ve. El typecheck
está contento: los tipos cuadran. El lint está contento: no hay variables sin
usar. Los tests están verdes: prueban lo que el código hace, no lo que promete.
Y una revisión de código normal tampoco los pilla, porque cada archivo por
separado es correcto.

El defecto vive **entre** los archivos: en la distancia entre lo que el sistema
dice que hace y lo que de verdad ocurre cuando un usuario lo usa. Una columna
que se escribe y nadie lee. Un badge que sale en el 100 % de las tarjetas. Un
30 % del ranking que multiplica siempre por el mismo número. Un teléfono con
índice `UNIQUE` que ningún formulario rellena.

Nada de eso rompe. Todo eso miente.

Tu trabajo es medir esa distancia y ordenar los hallazgos por **daño real al
usuario**, no por cantidad. Un badge decorativo es cosmético. Una métrica muerta
que pondera el 30 % del ranking es grave. Un contacto que desaparece el día en
que el huésped llega es urgente.

## Antes de empezar: construye el mapa

No audites a ciegas. Las tres primeras lecturas te ahorran la mitad del trabajo
y, sobre todo, te evitan el falso positivo más caro (ver más abajo):

1. **El esquema completo.** `supabase/migrations/*.sql` en orden cronológico.
   Anota cada columna, cada policy de lectura pública, cada trigger. Esta es tu
   lista de promesas del lado de los datos.
2. **La máquina de estados.** En Apúntate está en `lib/fsm/`. Necesitas la lista
   de estados y las transiciones para juzgar la categoría 6.
3. **Los ADRs y la documentación de fases.** `docs/`. Lo que esté declarado como
   trabajo futuro no es deuda invisible: es plan.

Con eso montado, recorre las ocho categorías. Cada una tiene su comando de
detección; los `rg` de abajo están escritos para ejecutarse desde la raíz del
repo. Si tienes la herramienta Grep disponible, úsala en vez de `rg` por consola
—los resultados salen enlazados—, pero conserva el mismo patrón.

---

## 1. Construido y nunca conectado

Existe el circuito entero —migración, trigger, RLS, formulario, validación— y
ninguna pantalla lo lee. El esfuerzo se hizo; el usuario no lo ve.

**Caso real.** Todo el sistema de reseñas de Apúntate estaba en pie: trigger de
derivación del promedio, policy `resenas_lectura_publica` con `USING (true)`
(`supabase/migrations/20260806010000_resenas_operables_y_rls.sql:110`),
formulario de estrellas, regla de mínimo tres opiniones. La ficha pública
mostraba `"4.5 de 5"` y nada más. La policy llevaba meses permitiendo leer las
reseñas y **ninguna consulta pública preguntaba por ellas**.

**Cómo detectarlo.** Toma cada tabla con lectura pública y pregunta si alguien
la lee desde una ruta pública:

```bash
# 1. tablas con policy de lectura abierta.
# En una sola pasada multiline: encadenar `rg -A2 | rg -B2` parece funcionar pero
# da falsos negativos en silencio, porque el segundo rg sólo ve el contexto que
# el primero dejó pasar. Si el USING queda a 3 líneas, desaparece sin error.
rg --multiline -n "CREATE POLICY[^;]*?USING \(true\)" supabase/migrations

# 2. por cada tabla encontrada, ¿la lee alguien?
rg -n "from\('resenas'\)" app lib components

# 3. y si la lee, ¿desde una ruta pública o sólo desde el panel privado?
rg -n "from\('resenas'\)" "app/(public)" lib/negocios
```

Si el paso 2 no devuelve nada, o el paso 3 devuelve sólo rutas autenticadas
cuando la policy es pública, tienes un hallazgo. El mismo patrón se aplica a
componentes: un `.tsx` que nadie importa, un endpoint que ningún `fetch` llama.

```bash
# componentes construidos que nadie monta
for f in $(rg --files components -g '*.tsx'); do
  n=$(basename "$f" .tsx)
  rg -q "\b$n\b" app lib components --glob "!$f" || echo "HUÉRFANO: $f"
done
```

---

## 2. Valor constante disfrazado de dato

Un campo que siempre vale lo mismo, pintado en la interfaz como si informara. El
usuario cree estar leyendo información y está leyendo decoración.

**Casos reales.**

- `lib/negocios/queries.ts` fijaba `resenas: 0` con la nota "conteo diferido", y
  la tarjeta pintaba el `(N)` sólo `if (n.resenas > 0)`. El paréntesis **no
  aparecía jamás**: un 4.5 sobre tres opiniones se veía idéntico a un 4.5 sobre
  trescientas. Hoy lee la columna real (`lib/negocios/queries.ts:154`).
- `verificado: true` sigue fijo en `lib/negocios/queries.ts:163`, porque la
  consulta ya filtra `status = 'verified'`. Consecuencia: en el directorio el
  badge "Verificado" lo llevan el 100 % de las tarjetas. **Un distintivo
  constante no distingue nada.**

Ese segundo caso enseña también dónde está el límite de la excepción por deuda
deliberada: el comentario de las líneas 156-162 explica el porqué y hasta avisa
de que no discrimina, lo cual es ejemplar como documentación —pero el daño en
pantalla sigue ahí. Un comentario honesto degrada la gravedad del hallazgo; no
lo elimina. Repórtalo como cosmético con la nota "ya documentado".

**Cómo detectarlo.** Busca literales en los objetos de retorno de las capas de
datos y comprueba si algo se renderiza condicionado a ellos:

```bash
# literales constantes en mappers y queries
rg -n ":\s*(true|false|0|null|\[\]),\s*(//|/\*|$)" lib components/**/queries.ts

# ¿alguien renderiza condicionado a ese campo?
rg -n "verificado|resenas\s*>" components app
```

La confirmación es siempre la misma pregunta: *¿existe algún dato de entrada que
haga que esto valga otra cosa?* Si la respuesta es no, la condición del render
es código muerto y el elemento es decoración.

---

## 3. Entrada muerta / salida muerta

Una columna que nadie escribe, o que nadie lee. Las dos duelen, pero de forma
distinta: la entrada muerta hace que una pantalla prometa un dato que nunca
llegará; la salida muerta gasta cómputo y espacio en algo que nadie mira.

**Casos reales.**

- `users.phone` tiene `UNIQUE` (`20260517170001_initial_schema.sql:18`) e índice
  parcial (línea 35), y **ninguna pantalla la escribe**: no existe formulario
  donde el cliente ponga su teléfono. `lib/negocios/perfil.ts:97` sí escribe un
  `phone`, pero es el de `negocios`, otra tabla. Mientras tanto
  `app/(app)/negocio/reservas/page.tsx:183` pinta un enlace `tel:` con
  `cliente.phone`, y la política de privacidad promete al negocio que verá el
  teléfono del cliente al confirmar. **Tres promesas apuntando a una columna
  vacía.**
- `solicitud_negocio.vista_at` e `ignorada_motivo`
  (`20260517170001_initial_schema.sql:229` y `:232`) existen en el esquema y no
  hay botón que los escriba.
- `negocios.total_reservas` se calcula por trigger y durante meses no se pintó
  en ninguna parte. Hoy sí (`lib/negocios/queries.ts:184`).

**Cómo detectarlo.** Recorre columna a columna y clasifica cada una en "se
escribe", "se lee", "ambas" o "ninguna":

```bash
# extrae los nombres de columna del esquema.
# Escribe -o -r por separado: el clúster `-or` lo rechazan algunas versiones de rg.
# Y sin -n, que la salida alimenta un bucle.
COLS="${TMPDIR:-/tmp}/columnas.txt"   # fuera del repo; usa el scratchpad si lo tienes
rg -o -r '$1' "^\s+(\w+)\s+(TEXT|NUMERIC|INT|BOOLEAN|TIMESTAMPTZ|UUID|JSONB)" \
  supabase/migrations/*_initial_schema.sql | sort -u > "$COLS"

# por cada una, cuenta archivos que la escriben y archivos que la leen
while read -r col; do
  esc=$(rg -l "\b$col\b" app/api lib 2>/dev/null | wc -l)
  leo=$(rg -l "\b$col\b" app components 2>/dev/null | wc -l)
  [ "$esc" -eq 0 ] && [ "$leo" -eq 0 ] && echo "HUERFANA: $col"
done < "$COLS"
```

Escribe siempre los ficheros intermedios fuera del repo: el scratchpad de la
sesión si tu entorno te da uno, y si no `${TMPDIR:-/tmp}`. Una auditoría no debe
dejar rastro en el árbol de trabajo del usuario: si aparece un `columnas.txt`
sin seguir en `git status`, has ensuciado justo lo que venías a limpiar.

Ese barrido da ruido: úsalo para ordenar candidatos, no como veredicto. Confirma
a mano leyendo el `update`/`insert` real. Un `select('*')` cuenta como lectura
sólo si el campo llega a pintarse.

Cuando encuentres una entrada muerta, **busca siempre la promesa asociada**: la
pantalla, el correo o el texto legal que dice que ese dato existe. Ahí está la
gravedad.

---

## 4. Métrica que pondera y nunca se calcula

La peor de todas, porque parece que el sistema decide y no decide. Un campo
entra en un scoring, en una ordenación o en un umbral, y ningún job lo escribe:
es siempre `NULL`, cae a un valor por defecto idéntico para todos y su peso no
ordena nada.

**Caso real.** `negocios.tasa_respuesta_pct` pesa el 30 % del score del matching
(`lib/matching/soft-score.ts:13` y `:57`) y ningún job la escribe. En
`lib/matching/soft-score.ts:32` cae al literal `?? 50` para todos los
candidatos: el 30 % del ranking multiplica siempre por 0.5. Lo más doloroso es
que **los datos crudos existen**: `solicitud_negocio.respondida_at` guarda todo
lo necesario para calcularla.

**Cómo detectarlo.** Para cada campo que participe en scoring, ordenación o
umbral, verifica que exista un escritor:

```bash
# campos que entran en el score
rg -n "pesos\.|score\s*=|\.order\(" lib

# por cada campo hallado, busca su escritor
rg -n "tasa_respuesta_pct" app/api lib/jobs supabase/migrations
```

La señal delatora es un `?? <literal>` sobre un campo que pondera. Un defecto
constante en un factor multiplicativo equivale a borrar ese factor del modelo.

Si el campo procede de un trigger o de un cron, comprueba que el cron **existe y
está desplegado** (en este repo, `.github/workflows/jobs.yml` y
`app/api/jobs/*`), no sólo que el código del job esté escrito.

---

## 5. Dos señales, un solo hecho

Redundancia que sobrepondera sin querer. Dos campos con nombres distintos que se
escriben siempre en el mismo sitio y en el mismo instante: para el modelo son
dos evidencias independientes; en la realidad son una.

**Caso real.** `ultima_sesion_at` sólo se escribe al enviar una oferta, junto a
`ultima_respuesta_at`, en la misma sentencia
(`app/api/ofertas/route.ts:130`). Así que "activo ahora" y "respondió hace un
momento" son el MISMO evento con dos redacciones, y el scoring les da
`0.2 + 0.1 = 30 %` del total sobre un único hecho
(`lib/matching/soft-score.ts:14-15`).

**Cómo detectarlo.** Agrupa los campos por punto de escritura. Si dos aparecen
siempre en el mismo `update`, son uno:

```bash
# updates con más de un campo temporal: candidatos a señal duplicada
rg -n "\.update\(\{[^}]*_at[^}]*_at" app/api lib --multiline
```

La confirmación es conceptual, no sintáctica: pregunta *¿puede uno de los dos
cambiar sin el otro?* Si no existe ningún camino de código donde eso ocurra, son
la misma señal y el peso combinado está inflado.

---

## 6. La condición que se queda corta en el tiempo

Una regla correcta en un estado y equivocada en el siguiente. Aparece cuando se
escribe `status === 'X'` pensando en el momento en que se escribió la pantalla,
sin mirar hacia dónde avanza la máquina de estados después.

**Caso real.** El bloque "contacto del cliente" del panel del negocio exigía
`status === 'CONFIRMED_OFFLINE_PAYMENT'` exacto. Pero un job mueve la reserva a
`IN_PROGRESS` en cuanto empieza el servicio: **el día que el huésped llegaba, el
negocio perdía su correo y su teléfono**. Se retiraba el dato justo cuando había
que llamarse. Hoy la condición es una lista
(`app/(app)/negocio/reservas/page.tsx:55`) y el comentario de las líneas 124-127
explica el porqué, que es como debe quedar un arreglo de esta categoría.

**Cómo detectarlo.** Enfrenta cada comparación exacta de estado con la FSM:

```bash
# comparaciones exactas de estado (sospechosas por definición)
rg -n "status\s*===\s*'[A-Z_]+'" app components lib

# la lista real de estados y transiciones
rg -n "'[A-Z_]{4,}'" lib/fsm supabase/migrations/*_seed_fsm.sql
```

Para cada acierto, hazte la pregunta que revela el defecto: **"¿qué pasa en el
estado siguiente?"** Si el dato o el botón debía seguir estando y desaparece,
tienes un hallazgo —y casi siempre urgente, porque falla en el momento de mayor
valor para el usuario. La contraria también cuenta: una acción destructiva que
sigue disponible en un estado donde ya no debería.

---

## 7. El comentario que miente

Documentación que afirma una garantía que el código no da. Es especialmente
dañino porque el siguiente que lea el archivo confiará en la afirmación en vez
de verificarla, y construirá encima.

Aquí "comentario" incluye todo lo que hace de promesa escrita: comentarios
`//` y `/* */`, docstrings, un README, y muy especialmente **un ADR con status
Accepted**. Un ADR es la forma más fuerte de promesa que tiene un repo —es la
fuente que se cita cuando alguien pregunta "¿por qué está hecho así?"— y por eso
un ADR desactualizado hace más daño que un comentario desactualizado: no engaña
a quien lee ese archivo, engaña a quien diseña el siguiente. Un caso real de
Apúntate: `docs/05-decisions/ADR-003-matching.md` documenta una fórmula de
scoring (`presupuesto 0.30 + reputación 0.30 + velocidad 0.20 + anti_flood
0.20`) que `lib/matching/soft-score.ts:11-16` ya no implementa —el factor
presupuesto desapareció y anti_flood se volvió filtro duro—, y `run.ts` sigue
citando el ADR como su fuente.

**Casos reales.**

- `components/shared/TopNav.tsx` decía "Mismo árbol de rutas que BottomNav (única
  fuente: ITEMS)" y eran **dos constantes separadas con contenido distinto**:
  `/reservas` estaba en escritorio y no en móvil. Hoy la fuente única existe de
  verdad en `components/shared/nav-items.ts`, y TopNav (`:8`) y BottomNav (`:7`)
  la importan.
- Un esquema documentaba "hard-delete del Storage a los 90 días si el negocio
  queda rejected (Ley 1581)" y **no existe job, ni cron, ni código** que lo haga.
  Aquí la promesa incumplida no es sólo técnica: es regulatoria.

**Cómo detectarlo.** Trata cada afirmación de un comentario como un test que hay
que ejecutar contra el código. Las que prometen garantías son las que hay que
mirar primero:

```bash
# comentarios que afirman garantías, unicidad, automatismos o plazos
rg -n "(única fuente|single source|siempre|nunca|garantiza|se borra|automátic|\
a los \d+ días|cada \d+ (min|hora|día))" --glob '*.{ts,tsx,sql,md}' app lib components supabase
```

Por cada acierto, escribe la afirmación como una frase falsable y búscala en el
código. "Se borra a los 90 días" ⇒ ¿dónde está el `delete`? ¿dónde está el cron
que lo llama? ¿está en `.github/workflows/`? Si la cadena se corta en cualquier
eslabón, el comentario miente.

---

## 8. Estados terminales por accidente

Un estado del que no se sale, o un aviso que llega a quien no puede actuar. No
es un error de código: es un agujero en el grafo.

**Casos reales.**

- Un negocio `rejected` ve el CTA "Corregir documentos" hacia
  `/negocio/onboarding`, y esa página redirige de vuelta a `/negocio/estado`
  para cualquier status distinto de `pending_verification`
  (`app/(app)/negocio/onboarding/page.tsx:34-35`). **Bucle cerrado**: el negocio
  no puede rehacer nada, y el CTA le invita a intentarlo.
- La notificación de "registro enviado" se inserta con el `negocio_id` del propio
  negocio, así que **le llega al negocio y no al equipo**: nadie avisa al admin
  de que hay cola de verificación.

**Cómo detectarlo.** Recorre cada estado de la FSM y comprueba dos cosas: que
exista camino de salida, y que quien debe enterarse se entere.

```bash
# ¿a dónde lleva cada CTA, y qué hace el destino?
rg -n "href=\"/negocio/" app components
rg -n "redirect\(" app

# ¿a quién se dirige cada notificación?
rg -n "from\('notificaciones'\).*insert|insert\(\{[^}]*negocio_id" app lib --multiline
```

La pregunta de confirmación para cada notificación es "¿quién tiene que actuar
al recibir esto, y es el destinatario?". Para cada estado: "si soy un usuario
aquí, ¿qué puedo hacer, y funciona?".

---

## El filtro obligatorio: deuda deliberada y falsos positivos

Antes de escribir un hallazgo en el informe, pásalo por este filtro. Un informe
con ruido se ignora entero, y entonces los hallazgos buenos también se pierden.

**Deuda deliberada no es hallazgo.** Si junto al código hay un comentario que
explica **el porqué** y **cuándo se resuelve** —o un ADR, o una tarea del
backlog referenciada—, el equipo ya lo sabe y ya decidió. No lo reportes como
descubrimiento. Como mucho, menciónalo en una lista aparte de "deuda ya
declarada" si el plazo venció.

**Haz esta comprobación con un comando, no a ojo.** Es el paso que más tiempo
ahorra y el que más falsos positivos evita, así que conviértelo en rutina: antes
de escribir cualquier hallazgo, busca el nombre exacto del artefacto sospechoso
—la columna, el tipo de notificación, la función, la ruta— en la documentación
de plan:

```bash
SOSPECHOSO="shortlist"   # el identificador tal cual aparece en el código
rg -n -i "$SOSPECHOSO" docs/*backlog* docs/05-decisions docs/*.md
```

Si aparece en una Story, un subtask o un ADR como trabajo pendiente, es plan:
va a la sección "Deuda ya declarada", no a los hallazgos. Si no aparece con el
nombre exacto, prueba con el término de producto en vez del término técnico
(`shortlisted` → "guardar", "favorito", "lista corta"; `total_reservas` →
"reservas completadas"): el backlog está escrito en lenguaje de usuario y el
código en lenguaje de esquema, y ese desfase es justo lo que hace que un
auditor apresurado reporte planes como defectos.

Ojo con el caso intermedio: un comentario que explica el porqué pero no el
cuándo (como el de `verificado: true`) documenta la causa sin cerrar el daño.
Repórtalo, pero degradado, y di explícitamente que ya está documentado. La
sinceridad del comentario merece reconocimiento, no un hallazgo inflado.

**El falso positivo más común: código de una fase futura.** Hooks preparados
para la Fase 3, columnas creadas para alimentar el dataset de entrenamiento,
endpoints que esperan al siguiente hito. Si el esquema, un ADR o el backlog lo
declaran como tal, **no es deuda invisible: es plan**. Un ejemplo del propio
repo: `ignorada_motivo` lleva el comentario "senal para dataset" en el esquema,
lo cual lo acerca a esta categoría —pero como no hay dataset ni consumidor
declarado en ningún ADR, sigue siendo entrada muerta. La regla práctica: exige
que la declaración de futuro esté **fuera** del propio archivo sospechoso (ADR,
backlog, migración). Un comentario que se autoexculpa no basta.

**Otros falsos positivos.** Campos de auditoría legal que existen para poder
demostrar algo si alguien pregunta (`consent_log`) y no para pintarse. Campos que
consume una herramienta externa (Supabase Studio, un dashboard, un export). Y
código que sí se usa pero por una vía que tu grep no ve: llamadas dinámicas,
`select('*')`, RPC. Ante la duda, lee el código; no reportes por ausencia de
grep.

---

## Priorización: por daño, no por cantidad

Ordena todo el informe con esta escala. La pregunta que decide el nivel es
siempre la misma: **¿qué pierde el usuario, y en qué momento?**

| Nivel | Criterio | Ejemplo |
|---|---|---|
| **P0 Urgente** | El fallo se materializa en el momento de mayor valor, o incumple una promesa legal/contractual | El contacto del cliente desaparece el día que el huésped llega |
| **P1 Grave** | El sistema aparenta decidir y no decide: rankings, matching, umbrales | El 30 % del score del matching multiplica siempre por el mismo número |
| **P2 Medio** | Una promesa visible al usuario no se cumple, pero hay alternativa | El circuito de reseñas existe y la ficha pública no las muestra |
| **P3 Cosmético** | Adorno sin daño funcional | Un badge "Verificado" que lleva el 100 % de las tarjetas |

Dos reglas para no equivocarte de nivel:

- **Cantidad no sube la gravedad.** Veinte columnas muertas en tablas internas
  siguen siendo P3 juntas. Una sola métrica muerta que ordena el marketplace es
  P1 sola.
- **La promesa sube la gravedad.** El mismo campo vacío es P3 si nadie lo
  menciona y P0 si la política de privacidad, un correo o un texto legal
  aseguran que el usuario lo verá.
- **Distingue daño activo de daño latente, sin bajar el nivel por ello.** Un
  ranking roto no le cuesta nada a nadie mientras haya menos candidatos que
  plazas: si el tope es 8 y en la ciudad hay 6 negocios, ninguna ordenación
  excluye a nadie todavía. Eso no rebaja el hallazgo —el defecto es el mismo y
  se activará solo, sin aviso, el día que el directorio crezca—, pero cambia la
  conversación sobre cuándo arreglarlo, y el usuario necesita saberlo. Dilo
  explícitamente en el hallazgo: "hoy latente porque X, activo en cuanto Y".
  Cuando dependa de volúmenes reales que no puedes ver desde el código, dilo en
  Cobertura en vez de suponerlo; si tienes acceso a la base de datos, cuéntalo.

Un aviso sobre el orden de arreglo que suele pasar desapercibido: cuando un
defecto de estos está a punto de ganar superficie pública —una métrica rota que
el backlog planea *mostrar* en pantalla— señálalo. Arreglar el cálculo antes de
construir la UI cuesta una tarde; después, cuesta una tarde más una regresión
visible para el usuario.

---

## Formato del informe

Escribe el informe en español, en el chat. No crees archivos `.md` de informe
salvo que te lo pidan. Usa esta estructura:

```markdown
# Auditoría de deuda invisible — <proyecto> — <fecha>

## Resumen
<3-5 frases: cuántos hallazgos, en qué categorías se concentran, y cuál es
el que arreglarías esta misma tarde si sólo pudieras hacer uno.>

## Hallazgos

### P0 — <título en una línea que diga el daño, no la causa>
- **Dónde**: `ruta/al/archivo.ts:123` (y los demás puntos implicados)
- **Qué promete**: <lo que un usuario, un comentario o un texto legal esperan>
- **Qué hace de verdad**: <el comportamiento observable>
- **Categoría**: <1-8>
- **Cómo se comprueba**: <comando concreto o pasos en la app que lo demuestran>
- **Arreglo sugerido**: <una o dos frases; el arreglo mínimo, no el ideal>

### P1 — ...
(mismo bloque)

## Deuda ya declarada (no son hallazgos)
<Lista breve de lo que encontraste pero está documentado con porqué y cuándo.
Marca los que hayan vencido su plazo.>

## Cobertura
<Qué categorías recorriste, qué zonas del repo no pudiste revisar y por qué.
Si una categoría salió limpia, dilo: un cero verificado también informa.>
```

Dos exigencias sobre el contenido de cada hallazgo:

- **`ruta:línea` siempre.** Un hallazgo sin ubicación exacta no es accionable y
  obliga al lector a repetir tu trabajo.
- **"Cómo se comprueba" tiene que ser reproducible.** El comando `rg` que lo
  revela, o los tres clics en la app que lo hacen visible. Esto es lo que separa
  esta auditoría de una opinión, y es lo que permite verificar el arreglo
  después.

Y tres reglas de redacción que resuelven las dudas que salen siempre:

- **Cómo citar una ausencia.** Buena parte de estos hallazgos son "esto no
  existe en ninguna parte", y ahí no hay línea que señalar. No lo dejes vago:
  cita en **Dónde** el punto que *consume* lo que falta —el `?? 50` que
  absorbe el `NULL`, la pantalla que pinta el campo vacío, el `CHECK` del
  esquema que declara el tipo—, y en **Qué hace de verdad** escribe el comando
  cuyo resultado vacío es la prueba. Una ausencia se demuestra con el grep que
  no devuelve nada, y ese grep es tan citable como una línea.
- **Cuando un hallazgo encaja en dos categorías, gana la más específica.** Es
  frecuente: una métrica muerta es a la vez columna sin escritor (3) y factor de
  scoring sin calcular (4). Reportarla como 4 dice mucho más sobre el daño que
  reportarla como 3. La categoría es una etiqueta para que el lector agrupe, no
  una taxonomía que haya que defender; elige la que mejor explique por qué duele
  y menciona la otra de pasada si aporta.
- **Un hallazgo por defecto, no por síntoma.** Si tres pantallas se rompen por
  la misma columna vacía, es un hallazgo con tres puntos en **Dónde**, no tres
  hallazgos. Inflar la cuenta hace que el informe parezca más grave y sea menos
  útil.

## Cómo recorrer el repo

Ve de lo que más duele a lo que menos, para que el informe sea útil aunque se
quede a medias:

1. **Scoring y ordenaciones** (categoría 4). Poco código, daño máximo.
2. **Condiciones de estado en pantallas de transacción** (categoría 6). Reservas,
   pagos, contacto: lo que falla en el peor momento.
3. **Esquema completo, columna a columna** (categorías 3 y 5). Es el barrido más
   largo; hazlo con script.
4. **Capas de datos y mappers** (categoría 2). Rápido, muchos aciertos pequeños.
5. **Circuitos completos y componentes huérfanos** (categoría 1).
6. **Comentarios con garantías** (categoría 7) y **grafo de estados**
   (categoría 8). Cierre.

Si el usuario acota el alcance ("mira sólo el matching", "revisa la ficha
pública"), respétalo y dilo en la sección de cobertura: el valor de esta
auditoría depende de que se sepa qué se miró y qué no.
