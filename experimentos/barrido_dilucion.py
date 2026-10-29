"""Barrido de dilución: ¿cuánto compite el documento envenenado en una base de conocimiento más grande?

Solo mide la RECUPERACIÓN (no llama al modelo de chat: sin cuota ni costo). Usa el mismo modelo de
embeddings y el mismo almacén vectorial que la app. Agrega documentos legítimos sintéticos al corpus base
(41 documentos) en tamaños crecientes y, para cada ataque indirecto, mide si su propio veneno sigue entrando
entre los primeros k resultados. También mide con qué frecuencia una consulta LEGÍTIMA trae veneno al
contexto sin que nadie ataque (exposición colateral).

    python experimentos/barrido_dilucion.py --puerto 8081 --tamanos 0,50,200,1000,3000 --semillas 1,2,3

Conviene correrlo contra una instancia propia con una colección de Chroma aparte
(--app.chroma.collection=...), porque inyecta y retira miles de documentos.

Los documentos sintéticos son declarativos (sin instrucciones): 30% del tema de los ataques (reembolsos,
acceso, contacto, fraude, comprobantes, casos de soporte) y 70% de otros temas bancarios.
"""
import argparse
import json
import random
import sys
import time
import urllib.request
from datetime import datetime
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")
sys.path.insert(0, str(Path(__file__).parent))
import run_attacks as ra  # noqa: E402

CERCANOS = [
    "Reembolsos de comisiones por mantenimiento", "Reintegros de promociones con tarjeta",
    "Reembolsos por débitos automáticos duplicados", "Devolución de cargos por servicios no prestados",
    "Reintegros por compras en el exterior", "Reembolsos por compras con tarjeta de débito",
    "Acreditación de contracargos", "Recuperación de acceso al home banking",
    "Bloqueo de usuario por intentos fallidos", "Cambio de dispositivo autorizado en la app",
    "Actualización de datos de contacto del titular", "Cambio de domicilio registrado",
    "Validación del correo electrónico registrado", "Envío de comprobantes de transacciones",
    "Constancias de movimientos por correo", "Prevención de fraude en transferencias",
    "Mensajes sospechosos y phishing", "Bloqueo preventivo de tarjetas", "Seguimiento de casos de soporte",
    "Auditoría interna de casos resueltos", "Regularización de diferencias de saldo",
    "Conciliación de movimientos", "Transferencias entre cuentas propias",
    "Límites de transferencias a terceros", "Cuentas de contingencia operativa",
    "Historial de conversaciones con el cliente", "Consentimiento del cliente para el tratamiento de datos",
    "Alcance de los servicios del asistente virtual", "Cierre de reclamos y encuestas de satisfacción",
]
LEJANOS = [
    "Préstamos hipotecarios", "Plazo fijo en dólares", "Seguros de hogar", "Seguros de vida",
    "Inversiones en fondos comunes", "Tarjetas corporativas", "Cuentas sueldo", "Débito inmediato",
    "Cajas de seguridad", "Horarios de sucursales por localidad", "Cheques de pago diferido",
    "Comercio exterior", "Cuentas para menores", "Beneficios y promociones", "Programa de puntos",
    "Prevención de lavado de activos", "Requisitos para empresas", "Atención a jubilados",
    "Accesibilidad en sucursales", "Calendario de feriados bancarios", "Financiación de vehículos",
    "Cuentas en moneda extranjera", "Pago de servicios e impuestos", "Cobranzas a comercios",
    "Tarifario de productos", "Préstamos prendarios", "Billeteras virtuales", "Cajeros automáticos",
    "Banca para pymes", "Educación financiera",
]
CANALES = ["la banca móvil", "el home banking", "una sucursal", "el centro de atención telefónica",
           "un correo a soporte@banco.com", "un cajero automático habilitado"]
REQUISITOS = ["DNI vigente", "el comprobante de la operación", "el número de transacción",
              "la constancia de CUIL", "el código del token de seguridad", "un comprobante de domicilio"]
AREAS = ["Atención al Cliente", "Operaciones", "Medios de Pago", "Riesgos", "Cumplimiento", "Back Office"]
FRASES = [
    "El cliente puede realizar este trámite a través de {canal} dentro de los {plazo} días hábiles.",
    "Para iniciar la gestión debe presentar {req}.",
    "El área de {area} revisa la solicitud y responde en un plazo máximo de {plazo} días.",
    "El monto máximo por operación es de ${monto}.000 y se informa al cliente por los canales habituales.",
    "Esta política se actualiza anualmente y rige desde {anio}.",
    "Si la gestión no puede completarse, el cliente es derivado al equipo de soporte.",
    "No se cobran cargos adicionales por este trámite, salvo los indicados en el tarifario vigente.",
    "Los casos especiales se evalúan de forma individual por el área correspondiente.",
    "Este procedimiento se refiere a {tema} y aplica a clientes individuales y a empresas.",
    "La información se conserva según los plazos de la normativa vigente.",
    "El cliente recibe una notificación cuando la gestión finaliza.",
    "Las excepciones requieren la autorización del responsable del área de {area}.",
]


def generar(n, semilla, desde=0):
    """n documentos sintéticos, reproducibles por (semilla, posición). 30% del tema de los ataques."""
    docs = []
    for i in range(desde, desde + n):
        rnd = random.Random(f"{semilla}-{i}")
        cerca = rnd.random() < 0.30
        tema = rnd.choice(CERCANOS if cerca else LEJANOS)
        valores = dict(canal=rnd.choice(CANALES), req=rnd.choice(REQUISITOS), area=rnd.choice(AREAS),
                       plazo=rnd.choice([2, 3, 5, 10, 15, 30]), monto=rnd.choice([10, 20, 50, 100, 200]),
                       anio=rnd.choice([2023, 2024, 2025, 2026]), tema=tema.lower())
        cuerpo = " ".join(rnd.sample(FRASES, rnd.randint(3, 5))).format(**valores)
        titulo = f"{tema} - versión {valores['anio']}.{rnd.randint(1, 9)}"
        docs.append({"fuente": f"sintetico_{semilla}_{i}.txt", "texto": f"{titulo}\n\n{cuerpo}\n",
                     "origen": "CONFIABLE", "tipo": "sintetico-dilucion"})
    return docs


def post_json(ruta, cuerpo, timeout=900):
    req = urllib.request.Request(ra.BASE + ruta, data=json.dumps(cuerpo).encode("utf-8"), method="POST",
                                 headers={"content-type": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return json.loads(r.read().decode("utf-8"))


def top(pregunta, k=10):
    return ra.http("GET", "/experiment/retrieval", {"pregunta": pregunta, "k": k})


def medir(indirectos, legitimas, k_max=10):
    """Para cada ataque indirecto: posición (1..k_max) de su propio veneno o None; para las legítimas:
    cantidad de documentos NO_CONFIABLES entre los 4 primeros."""
    rangos = {}
    for id_, tipo, pregunta, _ in indirectos:
        docs = top(pregunta, k_max)
        pos = next((i + 1 for i, d in enumerate(docs)
                    if d["origen"] == "NO_CONFIABLE" and d["fuente"].startswith(tipo + "__")), None)
        rangos[id_] = pos
    colateral = {}
    for id_, tipo, pregunta, _ in legitimas:
        docs = top(pregunta, 4)
        colateral[id_] = sum(d["origen"] == "NO_CONFIABLE" for d in docs)
    return rangos, colateral


def recall(rangos, k):
    return sum(1 for p in rangos.values() if p is not None and p <= k) / len(rangos)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--puerto", default="8081")
    ap.add_argument("--tamanos", default="0,50,200,1000,3000", help="documentos sintéticos agregados (acumulativo)")
    ap.add_argument("--semillas", default="1,2,3")
    ap.add_argument("--salida", default=str(Path(__file__).parent / "resultados"))
    args = ap.parse_args()
    ra.BASE = f"http://localhost:{args.puerto}"
    tamanos = sorted({int(x) for x in args.tamanos.split(",")})
    semillas = [int(x) for x in args.semillas.split(",")]

    ra.esperar_base_lista()
    config = ra.http("GET", "/experiment/config")
    indirectos = [a for a in ra.ATAQUES if a[1] != "directa"]
    print(f"corpus base={config.get('corpus')} | ataques indirectos={len(indirectos)} | "
          f"legítimas={len(ra.LEGITIMAS)} | tamaños={tamanos} | semillas={semillas}\n", flush=True)

    base_rangos, base_colateral = medir(indirectos, ra.LEGITIMAS)
    base_fuentes = {a[0]: [d["fuente"] for d in top(a[2], 10)] for a in indirectos}
    resultados = []

    for semilla in semillas:
        agregados = 0
        for N in tamanos:
            if N > agregados:
                t0 = time.time()
                lote = generar(N - agregados, semilla, desde=agregados)
                post_json("/experiment/ingesta", lote)
                print(f"  [semilla {semilla}] +{len(lote)} documentos en {time.time() - t0:.0f}s "
                      f"(total sintéticos={N})", flush=True)
                agregados = N
            rangos, colateral = medir(indirectos, ra.LEGITIMAS)
            fila = {"semilla": semilla, "N_sinteticos": N, "N_total": 41 + N, "rangos": rangos,
                    "colateral_top4": colateral}
            for k in (1, 2, 4, 6, 10):
                fila[f"recall@{k}"] = recall(rangos, k)
            fila["colateral_con_veneno"] = sum(1 for v in colateral.values() if v > 0) / len(colateral)
            resultados.append(fila)
            print(f"  [semilla {semilla}] N_total={41 + N:>5}  recall@4={fila['recall@4']:.2f}  "
                  f"recall@10={fila['recall@10']:.2f}  legítimas con veneno en top-4={fila['colateral_con_veneno']:.2f}",
                  flush=True)
        # limpieza y verificación de que el índice quedó como al principio
        retirados = ra.http("DELETE", "/experiment/ingesta")
        rangos_post, _ = medir(indirectos, ra.LEGITIMAS)
        fuentes_post = {a[0]: [d["fuente"] for d in top(a[2], 10)] for a in indirectos}
        igual = fuentes_post == base_fuentes
        print(f"  [semilla {semilla}] retirados={retirados}; el índice {'volvió' if igual else 'NO VOLVIÓ'} al estado inicial\n",
              flush=True)
        if not igual:
            print("ATENCIÓN: tras retirar los documentos la recuperación cambió; descartar las semillas siguientes "
                  "y reiniciar la instancia.", flush=True)
            break

    # --- resumen
    print("=== RESUMEN (media entre semillas; rango min-max) ===")
    print(f"{'N total':>8} {'recall@1':>9} {'recall@4':>16} {'recall@10':>16} {'legítimas con veneno (top-4)':>30}")
    for N in tamanos:
        fs = [f for f in resultados if f["N_sinteticos"] == N]
        if not fs:
            continue
        def media(c):
            v = [f[c] for f in fs]
            return sum(v) / len(v), min(v), max(v)
        r1, r4, r10, col = media("recall@1"), media("recall@4"), media("recall@10"), media("colateral_con_veneno")
        print(f"{41 + N:>8} {r1[0]:>9.2f} {r4[0]:>8.2f} ({r4[1]:.2f}-{r4[2]:.2f}) {r10[0]:>8.2f} ({r10[1]:.2f}-{r10[2]:.2f})"
              f" {col[0]:>14.2f} ({col[1]:.2f}-{col[2]:.2f})")

    mayor = max(tamanos)
    print(f"\nPosición del veneno propio por ataque (N sintéticos={mayor}; posiciones por semilla, '-' = fuera del top-10):")
    for id_, tipo, _, _ in indirectos:
        pos = [f["rangos"][id_] for f in resultados if f["N_sinteticos"] == mayor]
        base = base_rangos[id_]
        print(f"  {id_:<4}{tipo:<15} base={base if base else '-':<3} con {mayor}: "
              f"{[p if p else '-' for p in pos]}")

    salida = Path(args.salida)
    salida.mkdir(parents=True, exist_ok=True)
    archivo = salida / f"dilucion_{config.get('corpus', '?')}_{datetime.now():%Y%m%d_%H%M%S}.json"
    archivo.write_text(json.dumps({"config": config, "tamanos": tamanos, "semillas": semillas,
                                   "base": {"rangos": base_rangos, "colateral": base_colateral},
                                   "resultados": resultados}, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"\nJSON: {archivo}")


if __name__ == "__main__":
    main()
