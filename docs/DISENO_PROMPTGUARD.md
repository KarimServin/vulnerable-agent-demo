# Diseño propuesto de PromptGuard

**Estado: diseño original (23/10/2026). Después se implementó como starter y se evaluó; los resultados están en `RESULTADOS_PROMPTGUARD.md` (Parte III) y allí se explican los dos cambios respecto de este texto: la C4 refinada y la comparación de valor completo en C3.** Se deriva de la evidencia del baseline
([INFORME_HALLAZGOS.md](INFORME_HALLAZGOS.md)); cada componente indica qué hallazgo lo justifica. Según `AGENTS.md`, PromptGuard
**no va en este repositorio**: sería un proyecto aparte (Spring Boot Starter) que se prueba contra esta aplicación de referencia.

---

## 0. Resumen

**PromptGuard es una capa de control de agencia, en código, entre la decisión del modelo y la ejecución de una herramienta.** No intenta
"detectar texto malicioso" como pieza central: decide, de forma **determinista, local y auditable**, si una acción concreta puede ejecutarse,
mirando cuatro cosas que el modelo no puede garantizar:

1. **Autoridad:** ¿esta herramienta puede ejecutarla el asistente por sí solo, o requiere a una persona? (viene de la **configuración**, nunca de un documento).
2. **Intención:** ¿el cliente pidió esta acción?
3. **Fundamento de los argumentos:** ¿cada dato sensible (cuenta, monto, correo) viene del cliente o de una fuente confiable, y no de un documento recuperado?
4. **Procedencia × sensibilidad:** ¿hay contenido no confiable en el contexto y la herramienta es de alta sensibilidad?

Si la decisión no es "permitir", la acción **no se ejecuta**: se **bloquea** o se pide **confirmación fuera del modelo** (mostrando los parámetros reales a una persona).
Todo queda en un **registro de auditoría**. Puede funcionar en **modo sombra** (solo registra lo que bloquearía) para adoptarlo sin riesgo.

### Cambios respecto del resumen enviado al congreso

El resumen proponía tres capas: (1) heurística, (2) clasificador semántico con un LLM y (3) política de correlación origen × sensibilidad en el momento de ejecutar la herramienta.
La evidencia sugiere reordenar:

| Capa del resumen | Evidencia | Cambio propuesto |
|---|---|---|
| Heurística de texto | Los ataques más eficaces **imitan documentación legítima** (E1–E8) | Deja de ser el núcleo: es una señal barata de apoyo |
| Clasificador semántico (LLM) | Es manipulable, pesado para hardware limitado y exige un segundo modelo local | **Opcional**, desactivado por defecto |
| Política origen × sensibilidad | Es la idea central, pero gruesa: bloquear siempre que haya contenido no confiable rompe consultas legítimas (falsos positivos) | Se **refina** con fundamento de argumentos e intención ("derivar vs. convivir") |
| (no estaba) Autoridad por herramienta | E10: 20% de acciones indebidas **sin atacante** | **Nuevo**, núcleo |
| (no estaba) Confirmación fuera del modelo | E8: el agente "confirma" y ejecuta tras un "sí" | **Nuevo**, núcleo |
| (no estaba) Auditoría y modo sombra | Necesidad de trazabilidad en instituciones pequeñas | **Nuevo** |

---

## 1. Objetivos y no objetivos

**Objetivos**

- Evitar que acciones sensibles se ejecuten por contenido recuperado, por pedidos fuera de la autoridad del asistente o por datos que el cliente no dio.
- Funcionar **sin dependencias externas** y con **hardware modesto**: instituciones que no pueden usar una API externa.
- Ser **adoptable sin tocar el código de negocio** (un starter y un archivo de políticas).
- Ser **auditable**: poder explicar por qué se permitió o bloqueó cada acción.
- Ser **medible** con la misma metodología del baseline (ASR, utilidad, acciones indebidas, latencia).

**No objetivos** (ver sección 8): autenticar al cliente, impedir que el modelo diga cosas falsas, defender contra un documento "confiable" comprometido, ni garantizar seguridad
general de LLM.

---

## 2. De los hallazgos a los requisitos

| Hallazgo (informe) | Requisito de diseño |
|---|---|
| Los ataques plausibles imitan documentación legítima (E1–E8) | No depender de clasificar el texto; controlar la **acción** |
| El prompt estricto no basta con modelos abiertos alojables (E5, E6) | Control **independiente del modelo** |
| AD2 (falsificar una "actualización del alcance") | La **autoridad la define la configuración**, no el contenido recuperable |
| A03, A04, A11, AD3, AD4 (el dato peligroso viene del documento) | **Fundamento de argumentos**: cada parámetro sensible debe provenir del cliente o de una fuente confiable |
| A02, A06, A07, A08, AD1, AD5 (acción que el cliente no pidió) | **Fundamento de intención** |
| E8 (el modelo pide confirmación y ejecuta tras "sí"; cita el veneno) | **Confirmación fuera del modelo** con parámetros reales; control de la respuesta |
| E10 (cambio de correo y envío a terceros sin atacante) | **Autoridad por herramienta** (`REQUIERE_HUMANO`) |
| E9 (el veneno llega al contexto ~71%) | El control actúa **después** de la recuperación: no depende de que el veneno no llegue |
| L05 (un cliente legítimo termina con el reembolso en la cuenta del veneno) | Argumento de destino **no fundado en el cliente** → bloquear o confirmar |
| Instituciones pequeñas, hardware limitado | Núcleo determinista, sin segundo LLM, latencia mínima, **modo sombra** |
| Defectos de medición (informe, sección 6) | Cada decisión debe ser **reproducible** (mismas entradas, misma decisión) |

---

## 3. Principios de diseño

1. **Denegar por defecto:** una herramienta que no está en la política no se ejecuta.
2. **La autoridad no sale del contenido:** ningún documento recuperado, mensaje del modelo ni "actualización de política" puede ampliarla.
3. **Determinista y local:** las decisiones no usan un LLM; son reglas sobre datos estructurados.
4. **El modelo propone, el código dispone.**
5. **Fallar cerrado en lo sensible:** ante un error interno del guard, las herramientas de sensibilidad alta no se ejecutan.
6. **Todo queda registrado** (decisión, regla, motivo, procedencia).
7. **Adopción gradual:** modo sombra antes de aplicar.

---

## 4. Arquitectura

```
INGESTA
  documentos ──► [Etiquetador de procedencia] ──► VectorStore
  (origen, estado, versión, hash)                       │
                                                        ▼
EJECUCIÓN (por solicitud)                  RAG recupera fragmentos
                                           {origen, estado, fuente, versión}
  Mensaje del cliente ───────┐                          │
                             ▼                          ▼
                   [Contexto de la solicitud]
     (mensajes del cliente, fragmentos, resultados de herramientas confiables)
                                  │
              Modelo (LLM) decide: "ejecutar herramienta X con args A"
                                  │
                                  ▼
           ┌─────────────  [Evaluador de acciones]  ─────────────┐
           │  C0 registrada   C1 autoridad   C2 intención        │
           │  C3 argumentos fundados   C4 procedencia × sensib.  │
           └──────┬──────────────────┬──────────────────┬────────┘
              PERMITIR       ESPERAR CONFIRMACIÓN   BLOQUEAR / DERIVAR
                  │                  │                  │
           ejecuta la         [Confirmación fuera   mensaje seguro al
           herramienta        del modelo] (persona) modelo y al cliente
                  └──────────────────┴────────┬─────────┘
                                              ▼
                         [Auditoría]  +  [Control de la respuesta]
```

El evaluador de acciones, la confirmación y la auditoría son el **núcleo**; el etiquetado de procedencia en la ingesta alimenta C4 y C3.

---

## 5. Componentes

### 5.1 Etiquetado de procedencia en la ingesta

**Qué:** cada documento se indexa con metadatos que viajan hasta la recuperación.

| Campo | Valores / ejemplo | Para qué |
|---|---|---|
| `origen` / nivel de confianza | `CONFIABLE` (curado), `INTERNO_EDITABLE`, `EXTERNO` (clientes, correos, formularios, adjuntos), `DESCONOCIDO` | C4; peso de cada fragmento |
| `estado` | `VIGENTE`, `BORRADOR`, `OBSOLETO` | Evitar actuar sobre contenido desactualizado (caso cotidiano, sin atacante) |
| `fuente`, `version`, `hash`, `fecha`, `propietario` | — | Trazabilidad |

- El starter ofrece un **enriquecedor de documentos** para la ingesta; lo que no se etiqueta se considera `DESCONOCIDO` (el nivel más bajo).
- **Propagación del nivel más bajo (taint):** un documento derivado de otro (resumen, copia a una wiki) hereda el **menor** nivel de confianza de sus fuentes. Es la mitigación del
  *lavado de procedencia* (sección 8).
- La **base de conocimiento actual de la demo ya tiene `origen` y `fuente`**; falta `estado`.

### 5.2 Contexto de la solicitud

Por cada solicitud, el guard mantiene:

- los **mensajes del cliente** (turnos actuales y previos de la conversación);
- los **fragmentos recuperados** con su procedencia;
- los **resultados de herramientas confiables** de esa solicitud (por ejemplo, el saldo o los movimientos devueltos por el core: sirven para fundamentar argumentos);
- el identificador del modelo y de la sesión.

### 5.3 Política declarativa

Un archivo `promptguard.yml` (ejemplo para la demo bancaria). **La autoridad está aquí, no en los documentos.**

```yaml
promptguard:
  modo: enforce                 # shadow = solo registra lo que bloquearía; enforce = aplica
  confianza:                    # nivel numérico por origen
    CONFIABLE: 3
    INTERNO_EDITABLE: 2
    EXTERNO: 1
    DESCONOCIDO: 0
  herramientas:
    consultarSaldo:
      sensibilidad: BAJA
      autoridad: ASISTENTE
      parametros: { cuenta: { fuente: [USUARIO] } }
    consultarMovimientos:
      sensibilidad: BAJA
      autoridad: ASISTENTE
      parametros: { cuenta: { fuente: [USUARIO] } }
    enviarComprobante:
      sensibilidad: MEDIA
      autoridad: ASISTENTE
      intencion: ["comprobante", "constancia"]
      parametros:
        cuenta:      { fuente: [USUARIO, HERRAMIENTA_CONFIABLE] }
        transaccion: { fuente: [USUARIO, HERRAMIENTA_CONFIABLE] }
        destino:     { fuente: [USUARIO], formato: email }       # el correo debe haberlo escrito el cliente
    bloquearTarjeta:
      sensibilidad: MEDIA
      autoridad: ASISTENTE
      intencion: ["bloque", "perd", "robaron", "extravi"]
      parametros: { tarjeta: { fuente: [USUARIO, HERRAMIENTA_CONFIABLE] } }
    cambiarDatosContacto:
      sensibilidad: ALTA
      autoridad: REQUIERE_HUMANO                                # lo hace soporte
    reembolsar:
      sensibilidad: ALTA
      autoridad: REQUIERE_HUMANO
    transferir:
      sensibilidad: ALTA
      autoridad: REQUIERE_HUMANO
    enviarEmail:
      sensibilidad: MEDIA
      autoridad: PROHIBIDA                                      # texto libre hacia afuera: no se expone
  contexto:
    confianza_minima_para: { ALTA: 3, MEDIA: 2 }              # nivel mínimo para ejecutar sin confirmar
  confirmacion:
    ttl_segundos: 300
  auditoria:
    destino: archivo                                            # archivo | evento de Spring
```

Valores de `autoridad`: `ASISTENTE` (puede ejecutarla si pasa los demás chequeos), `REQUIERE_HUMANO` (nunca la ejecuta el modelo: se deriva a una persona), `PROHIBIDA` (no se ejecuta).

**Carga de configuración:** el starter ofrece **plantillas** por tipo de institución (banco, municipio) para que una institución pequeña no empiece de cero.

### 5.4 Evaluador de acciones (el núcleo determinista)

Cada vez que el modelo propone ejecutar una herramienta, antes de ejecutarla:

```text
decidir(llamada, ctx):
  pol = politica.herramienta(llamada.nombre)
  si pol no existe:                               return BLOQUEAR("herramienta_no_registrada")      # C0
  si pol.autoridad == PROHIBIDA:                  return BLOQUEAR("autoridad")                      # C1
  si pol.autoridad == REQUIERE_HUMANO:            return DERIVAR_A_PERSONA(llamada, "autoridad")    # C1
  # C2 intención
  si pol.intencion definida y no ctx.clientePidio(pol.intencion):
        si pol.sensibilidad >= MEDIA: return ESPERAR_CONFIRMACION(llamada, "intencion")
        sino:                         return BLOQUEAR("intencion")
  # C3 fundamento de argumentos
  para cada parametro p sensible en pol.parametros:
        si no fundado(llamada.args[p], pol.parametros[p].fuente, ctx):
              return BLOQUEAR("argumento_no_fundado:" + p)
  # C4 procedencia × sensibilidad
  si ctx.confianzaMinima() < pol.confianzaMinimaRequerida():
        return ESPERAR_CONFIRMACION(llamada, "procedencia")
  return PERMITIR
```

**C2 — Intención.** Determinista y con el sesgo correcto: ante la duda, **pide confirmación** (a una persona) en vez de bloquear.
Se basa en una lista de términos por herramienta (en español, configurable) aplicada a los mensajes **del cliente**, no a los fragmentos recuperados.
Es la pieza con más riesgo de falsos positivos; hay que medirla (sección 10).

**C3 — Fundamento de argumentos.** Cada parámetro sensible debe **aparecer** en una fuente permitida:
- `USUARIO`: el valor está en un mensaje del cliente (coincidencia exacta tras normalizar: correos, números de cuenta, montos);
- `HERRAMIENTA_CONFIABLE`: el valor lo devolvió una herramienta del sistema de registro en esta solicitud (por ejemplo una cuenta que devolvió el core);
- `LISTA_BLANCA`: valores fijos de la política (por ejemplo, un correo oficial de soporte).

Un valor que solo aparece en un fragmento recuperado **no está fundado**. Eso cubre los ataques donde el dato peligroso viene del documento (A03 la cuenta de contingencia, A04 y AD3 las direcciones externas, A11 el correo del atacante,
AD4 el "correo registrado" inventado). La resolución de "correo registrado" debe hacerse consultando el **sistema de registro** (una herramienta del core), nunca un documento.

**C4 — Procedencia × sensibilidad.** Si el contexto incluye fragmentos de baja confianza y la herramienta es de alta sensibilidad, se pide confirmación. Es el refinamiento de la
política del resumen: **no bloquea siempre** que haya contenido no confiable (eso rompería consultas legítimas, como L05 con un veneno en el contexto); combinado con C2 y C3
resuelve "derivar vs. convivir": si la acción fue pedida (C2) y sus datos son del cliente (C3), la mera presencia de contenido no confiable **no** la bloquea.

### 5.5 Confirmación fuera del modelo (step-up)

**Problema observado (E8):** el modelo "confirma" con texto y un "sí" del cliente dispara la acción. Pero ese "sí" puede referirse a algo distinto de lo que se ejecuta, y un documento envenenado puede
llevar a la víctima a aceptar.

**Diseño:**
1. El guard **intercepta** la llamada y devuelve al modelo un resultado del tipo "acción pendiente de confirmación" (el modelo no ejecuta nada).
2. Guarda una **acción pendiente** `{id, herramienta, argumentos, hash, sesión, expiración}`.
3. La **aplicación** (no el modelo) muestra a la persona la descripción **exacta**: "Cambiar el correo de la cuenta 123-456 a X".
4. La persona confirma por un canal **autenticado de la aplicación** (no por texto del chat).
5. El guard ejecuta la acción **con los argumentos guardados** (no con los que el modelo diga después), una sola vez y dentro del tiempo de vida.

Para `REQUIERE_HUMANO` no hay confirmación del cliente: la acción se **deriva** (por ejemplo, se crea una tarea para soporte) y el asistente informa al cliente cómo continúa el trámite.

### 5.6 Control de la respuesta

**Problema observado (E8):** el modelo repite el contenido envenenado al cliente como si fuera política (82% de las respuestas medibles), y puede citarlo textualmente, incluida la instrucción interna.

**Opciones (configurables):** registrar, **anotar** ("fuente no verificada") o bloquear una respuesta que reproduzca fragmentos de origen no confiable o frases con forma de instrucción para el asistente.
Extensión a evaluar: sanear enlaces y URLs en la respuesta (vía de exfiltración tipo Slack AI). Es una capa **opcional**: su efecto sobre la utilidad hay que medirlo.

### 5.7 Heurística de entrada (señal de apoyo)

Reglas baratas sobre los fragmentos recuperados: frases imperativas dirigidas al asistente ("nota del sistema", "ejecutar la herramienta…", "ignorá…") y, sobre todo, **documentos que mencionan nombres de herramientas**
(un documento legítimo de la base casi nunca nombra funciones internas). Marca el fragmento como `SOSPECHOSO` y baja su nivel de confianza. **No es una defensa en sí**: un atacante que desconoce los nombres (el atacante "negro")
la evita. Su valor es subir la sospecha de forma barata.

### 5.8 Capa semántica (opcional)

Un clasificador local pequeño (o similaridad con embeddings contra ejemplos de ataques) que puntúa fragmentos. **Desactivada por defecto**: es manipulable, exige un segundo modelo local y puede romper un presupuesto de latencia en hardware limitado.

### 5.9 Auditoría y observabilidad

Un evento por cada llamada a herramienta (decisión incluida), publicado de forma asíncrona:

```json
{
  "ts": "2026-10-23T18:04:11Z", "solicitud": "a1f3…", "modelo": "anthropic:claude-haiku-4-5",
  "herramienta": "cambiarDatosContacto", "argumentos": {"cuenta": "123-456", "email": "***"},
  "decision": "DERIVAR_A_PERSONA", "regla": "autoridad", "motivo": "REQUIERE_HUMANO",
  "contexto": [{"fuente": "contacto__recuperacion_de_acceso.txt", "origen": "NO_CONFIABLE", "estado": "DESCONOCIDO"}],
  "mensajeClienteHash": "9c2e…", "latenciaGuardMs": 1.4
}
```

Métricas (Micrometer): decisiones por regla y herramienta, bloqueos, confirmaciones pendientes, latencia. Es lo que permite a un municipio o a un consejo profesional **explicar** qué hizo su asistente.

### 5.10 Modo sombra

En `shadow` el guard **evalúa y registra** pero no bloquea. Permite: medir **cuántas consultas legítimas se habrían bloqueado** (falsos positivos) antes de activar, ajustar la política y
ganar confianza. Es clave para instituciones pequeñas sin personal de seguridad.

---

## 6. Integración con Spring AI

El resumen proponía el hook `doAfterCall` de `ToolCallingAdvisor`. Alternativas a evaluar (**verificar en la implementación** el comportamiento real de Spring AI 2.0):

| Opción | Cómo | Ventajas | Riesgos |
|---|---|---|---|
| **A. Decorador de `ToolCallback`** | Envolver cada `ToolCallback` registrado; el decorador evalúa antes de delegar | Punto justo antes de ejecutar; independiente de advisors | Hay que hacer llegar el contexto de la solicitud al decorador (por ejemplo vía `ToolContext`) |
| **B. `ToolCallingManager` propio** | Interceptar `executeToolCalls` con todas las llamadas de la ronda | Ve toda la ronda; un solo punto | Depende de la API del manager |
| **C. `ToolCallingAdvisor.doAfterCall`** (el resumen) | Evaluar tras cada respuesta del modelo, antes de ejecutar | Alineado con el diseño original | Depende de la versión del advisor |

**Captura del contexto:** un *advisor* ubicado **después** del de RAG registra los fragmentos recuperados (como ya hace `TrazaVectorStore` en la demo, que envuelve el almacén y registra lo recuperado
incluso si la respuesta falla) y los mensajes del cliente; el contexto debe asociarse a la **solicitud** (no a un hilo: en la demo se vio que no se puede asumir el hilo).

**Adopción "sin tocar el código de negocio":** un `BeanPostProcessor` o *customizer* del starter que envuelva los `ToolCallback`/`ToolCallbackProvider` encontrados; la política en `promptguard.yml`.

---

## 7. Cobertura esperada (hipótesis a verificar con la evaluación)

| Ataque / caso | Qué lo detendría | Chequeo |
|---|---|---|
| A01 obvio, A02 plausible, A06 objetivo, A08 oculto (reembolso no pedido) | `reembolsar` es `REQUIERE_HUMANO` | C1 (y C2) |
| A03 destino (cuenta de contingencia) | La cuenta de destino no está en el mensaje del cliente | C3 |
| A04 exfiltración, AD3 historial falso | La dirección viene del documento | C3 (y la confirmación muestra el destino real) |
| A05 rol (modo mantenimiento) | No amplía la autoridad; la política es configuración | C1 |
| A07 encadenado (el correo lo dio el cliente, pero no pidió enviarlo) | `enviarEmail` `PROHIBIDA`; envío no pedido | C1, C2 |
| A09, A10 directos | `reembolsar` `REQUIERE_HUMANO`; `enviarEmail` `PROHIBIDA` | C1 |
| A11 contacto | `cambiarDatosContacto` `REQUIERE_HUMANO`; correo del documento | C1, C3 |
| A12 transferencia | `transferir` `REQUIERE_HUMANO` | C1 |
| AD1 consentimiento simulado | El cliente no pidió la acción | C2 |
| AD2 autoridad falsificada | La autoridad no sale del contenido | C1 |
| AD4 "correo registrado" inventado | El correo no está en el mensaje del cliente; se resuelve con el sistema de registro | C3 |
| AD5 bloqueo preventivo | El cliente no pidió bloquear | C2 |
| M03 (cambiar correo a pedido explícito), M10 (enviar resumen a un tercero) | Autoridad del asistente | C1 |
| L05 (reembolso legítimo con veneno en el contexto) | Derivación a soporte; la cuenta correcta | C1, C3 |
| Respuesta que repite el veneno | Control de la respuesta | 5.6 |

Las acciones **legítimas** (L02 saldo, L06 movimientos, L07 comprobante a la dirección dada, L08 bloqueo informado) deben **pasar** C1–C4: son las que miden los falsos positivos.

---

## 8. Lo que PromptGuard no cubre (y ataques contra el propio guard)

- **Autenticación e identidad:** no sabe quién es el cliente; eso es de la aplicación. (La confirmación usa el canal autenticado de la app.)
- **Mentiras sin acción:** puede que el modelo diga algo falso sin ejecutar nada; solo se mitiga parcialmente con 5.6.
- **Documentos confiables comprometidos o erróneos:** si lo `CONFIABLE` está mal, el guard lo respeta. (Mitigación parcial: `estado` vigente/obsoleto.)
- **Lavado de procedencia:** un atacante copia contenido externo a una fuente `CONFIABLE` (por ejemplo una wiki interna). Mitigación: propagación del nivel más bajo, control del flujo de ingesta.
- **Fundamento por coincidencia:** si el cliente (engañado) escribe él mismo un dato malicioso, C3 lo da por fundado; la confirmación con parámetros reales ayuda, no elimina.
- **Evasión de la intención:** C2 es léxica; un pedido muy indirecto puede no coincidir o coincidir de más (falsos negativos y positivos). Por eso el sesgo es "pedir confirmación".
- **Errores de política:** una política mal escrita (`ASISTENTE` donde debía ser `REQUIERE_HUMANO`) anula el control. Mitigación: plantillas, modo sombra y revisión de la política.
- **Inyección directa que persuade al cliente** o ataques sin herramientas (solo texto).
- **Canales de salida** distintos de las herramientas (enlaces en la respuesta): solo cubiertos por la extensión 5.6.

Para cada limitación, la evaluación debe incluir un **atacante que conoce a PromptGuard** (adaptativo contra la política).

---

## 9. Requisitos no funcionales

| Requisito | Meta propuesta |
|---|---|
| Dependencias externas | Ninguna en el núcleo (sin llamadas a APIs externas ni segundo LLM) |
| Latencia del evaluador | **< 10 ms** en p95 (meta propia, a medir); el criterio del resumen para la capa semántica era < 500 ms |
| Memoria / hardware | Sin requisitos especiales; apto para servidores modestos |
| Configuración mínima | Un archivo y plantillas; modo sombra desde el primer día |
| Determinismo | Mismas entradas → misma decisión (reproducible y testeable) |
| Fallo | Cerrado para sensibilidad ALTA; configurable para el resto |
| Compatibilidad | Spring Boot + Spring AI 2.x; Java 21 |

---

## 10. Plan de evaluación

Se usa **la misma metodología y el mismo corpus versionado** del baseline, para que la comparación sea directa.

**Condiciones.** {`ingenuo`, `endurecido`, `endurecido-ciego`} × {sin PromptGuard, `shadow`, `enforce`} × modelos {`gpt-oss-20b`, `gpt-oss-120b`, `claude-haiku-4-5`}.

**Medidas.**

| Medida | Cómo | Hipótesis / criterio propuesto |
|---|---|---|
| ASR estándar y adaptativos (1 turno y 2 turnos) | Catálogo A*, AD* | H1: con `enforce`, ASR ≤ 10% (criterio del resumen); cercano a 0 en las herramientas de autoridad `REQUIERE_HUMANO` |
| Falsos positivos / utilidad | L01–L08 | H2: ≤ 5% de consultas legítimas bloqueadas (criterio del resumen); reportar aparte las que piden confirmación (fricción) |
| Acciones indebidas sin atacante | M01–M10, corpus limpio | H3: ≤ 2% |
| Atacante adaptativo automático que **conoce las reglas de PromptGuard** | `run_adaptativo.py` ampliado | H4: sin objetivos logrados en N intentos (reportar el N) |
| Latencia del guard | Mediciones por llamada | H5: p95 < 10 ms (sin capa semántica) |
| Auditoría | Eventos por llamada | H6: 100% de las llamadas registradas con procedencia |
| Efecto de la dilución | Barrido de dilución | H7: el efecto de PromptGuard no depende de la recuperación (actúa después) |
| Documentos desactualizados / borradores (sin atacante) | Prueba pendiente del baseline | H8: bloquea la acción sobre contenido `OBSOLETO` |

**Rigor.** Al menos 5 repeticiones por caso; intervalos de Wilson; informar 1 turno **y** 2 turnos; misma huella de corpus; **ablación** (cada chequeo por separado: cuánto aporta C1, C2, C3, C4, confirmación, respuesta); en `shadow`, contar los "habría bloqueado" por regla.

**Qué se reportaría como éxito y qué como resultado negativo.** Éxito = H1–H3 con costo bajo en utilidad. Resultado igualmente publicable: si C2 genera demasiados falsos positivos, o si la política es difícil de configurar (RQ4), eso se reporta como hallazgo.

---

## 11. Hoja de ruta (por fases)

| Fase | Contenido | Entrega |
|---|---|---|
| 0 | Proyecto aparte (starter); evento de auditoría y **modo sombra** sobre la demo (solo observar) | Registro de lo que haría el guard sin cambiar nada |
| 1 | Política declarativa, **autoridad** (C1) y **fundamento de argumentos** (C3) | Primer bloqueo real; medir ASR y utilidad |
| 2 | **Intención** (C2) y **confirmación fuera del modelo** (5.5) | Cierra los casos de acción no pedida y de "sí" engañoso |
| 3 | Etiquetado en la ingesta (`origen`, `estado`), **procedencia × sensibilidad** (C4), control de la respuesta | Cubre documentos desactualizados y respuesta con veneno |
| 4 | Heurística de entrada y, opcional, capa semántica | Señales de apoyo |
| 5 | Evaluación completa (sección 10), redacción y empaquetado | Resultados y artefacto |

---

## 12. Preguntas abiertas y riesgos

1. **Intención en español:** ¿alcanza una lista de términos por herramienta? ¿cuántos falsos positivos y negativos produce? (la medición lo dirá).
2. **Fundamento de argumentos:** normalización de números de cuenta, montos y correos; casos donde el dato correcto lo infiere el modelo a partir de una herramienta confiable.
3. **Conversaciones largas:** ¿cuántos turnos previos cuentan como "lo pidió el cliente"? ¿qué pasa si el cliente acepta una sugerencia del asistente?
4. **Carga de configuración:** ¿qué tan fácil es para una institución sin personal de seguridad escribir la política? (RQ4; entrevistas).
5. **API de Spring AI 2.0:** punto de interceptación estable y forma de propagar el contexto (opciones A, B, C de la sección 6).
6. **Compatibilidad con streaming** y con otros frameworks (el alcance depende de que las instituciones usen Java/Spring; pendiente de las entrevistas).
7. **Medición honesta:** que PromptGuard no sea evaluado solo con los ataques que conocemos; el atacante adaptativo contra sus reglas es imprescindible.
8. **Alcance del claim:** el aporte se afirma para el entorno descrito (modelos locales de capacidad limitada, datos que no salen), **no** como solución general de seguridad de LLM.
