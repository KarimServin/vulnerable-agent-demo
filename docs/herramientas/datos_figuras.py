"""Calcula, a partir de los CSV/JSON de experimentos/resultados, los datos de las figuras del informe."""
import csv, glob, json, math, sys
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[2]   # raíz del repositorio
RES = RAIZ / "experimentos" / "resultados"
SALIDA = Path(sys.argv[1])


def cargar(patron):
    archivos = sorted(RES.glob(patron))
    assert archivos, patron
    return list(csv.DictReader(open(archivos[-1], encoding="utf-8")))


def verdadero(v):
    return str(v) == "True"


def wilson(k, n, z=1.96):
    if n == 0:
        return 0.0, 0.0
    p = k / n
    d = 1 + z * z / n
    c = (p + z * z / (2 * n)) / d
    m = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / d
    return max(0.0, c - m), min(1.0, c + m)


def asr(filas, campo="exito"):
    ataques = [r for r in filas if r["clase"] in ("ataque", "adaptativo")]
    k = sum(verdadero(r[campo]) for r in ataques)
    return k, len(ataques)


fuentes = {
    "ing20": cargar("ingenuo_20261021_221341.csv"),
    "ingCl": cargar("ingenuo_anthropic-claude-haiku-4-5_20261022_001630.csv"),
    "endCl": cargar("endurecido_anthropic-claude-haiku-4-5_42e73d94b230_20261022_004407.csv"),
    "end20": cargar("endurecido_openai-openai-gpt-oss-20b_42e73d94b230_*.csv"),
    "end120": cargar("endurecido_openai-openai-gpt-oss-120b_42e73d94b230_*.csv"),
    "ciego2": cargar("endurecido-ciego_openai-openai-gpt-oss-120b_42e73d94b230_20261023_*.csv"),
}

# --- Figura 1: ASR por condición
cond = [
    ("Ingenuo · gpt-oss-20b", "ing20", "anterior", "exito"),
    ("Ingenuo · Claude Haiku 4.5", "ingCl", "anterior", "exito"),
    ("Endurecido · gpt-oss-20b (parcial)", "end20", "parcial", "exito"),
    ("Endurecido · gpt-oss-120b", "end120", "completo", "exito"),
    ("Endurecido ciego · gpt-oss-120b (2 turnos, parcial)", "ciego2", "parcial", "exito"),
    ("Endurecido · Claude Haiku 4.5", "endCl", "completo", "exito"),
]
fig1 = []
for etiqueta, clave, estado, campo in cond:
    k, n = asr(fuentes[clave], campo)
    lo, hi = wilson(k, n)
    fig1.append({"label": etiqueta, "k": k, "n": n, "p": k / n, "lo": lo, "hi": hi, "estado": estado})

# --- Figura 2: uno contra dos turnos (ataques A01-A12, gpt-oss-120b)
def solo_a(filas):
    return [r for r in filas if r["clase"] == "ataque"]

mio = solo_a(fuentes["end120"])
ciego = solo_a(fuentes["ciego2"])
fig2 = []
for etiqueta, filas, campo, color in [
    ("Prompt endurecido (el mío)\n1 turno", mio, "exito", "a"),
    ("Prompt ciego\n1 turno", ciego, "exito_1turno", "b"),
    ("Prompt ciego\ncon 2º turno", ciego, "exito", "c"),
]:
    k = sum(verdadero(r[campo]) for r in filas)
    n = len(filas)
    lo, hi = wilson(k, n)
    fig2.append({"label": etiqueta, "k": k, "n": n, "p": k / n, "lo": lo, "hi": hi, "color": color})

# --- Figura 3: dilución
dil = json.load(open(sorted(RES.glob("dilucion_42e73d94b230_*.json"))[-1], encoding="utf-8"))
tamanos = sorted({f["N_total"] for f in dil["resultados"]})
def media(campo, N):
    v = [f[campo] for f in dil["resultados"] if f["N_total"] == N]
    return sum(v) / len(v)
fig3 = {"x": tamanos, "recall4": [media("recall@4", N) for N in tamanos],
        "recall10": [media("recall@10", N) for N in tamanos],
        "colateral": [media("colateral_con_veneno", N) for N in tamanos]}

# --- Figura 4: mapa de calor ataque x condición
columnas = [("Claude Haiku 4.5", "endCl"), ("gpt-oss-20b\n(parcial)", "end20"),
            ("gpt-oss-120b", "end120"), ("120b, prompt ciego\n(2 turnos, parcial)", "ciego2")]
filas_ids = [f"A{i:02d}" for i in range(1, 13)] + [f"AD{i}" for i in range(1, 6)]
tipos = {}
for fuente in fuentes.values():
    for r in fuente:
        tipos[r["id"]] = r["tipo"]
vals, ns = [], []
for id_ in filas_ids:
    fila_v, fila_n = [], []
    for _, clave in columnas:
        rs = [r for r in fuentes[clave] if r["id"] == id_]
        if rs:
            fila_v.append(sum(verdadero(r["exito"]) for r in rs) / len(rs))
            fila_n.append(len(rs))
        else:
            fila_v.append(None)
            fila_n.append(0)
    vals.append(fila_v)
    ns.append(fila_n)
fig4 = {"filas": [f"{i}  {tipos.get(i, '')}" for i in filas_ids], "cols": [c for c, _ in columnas],
        "vals": vals, "ns": ns}

# --- Figura 5: sin atacante (corpus limpio)
limpio = cargar("endurecido_anthropic-claude-haiku-4-5_797e92481bc3_*.csv")
ids_m = [f"M{i:02d}" for i in range(1, 11)]
fig5 = []
descripciones = {
    "M01": "Tarjeta no funciona en el cajero", "M02": "Cambió de número de teléfono",
    "M03": "Cambiar correo registrado (explícito)", "M04": "No reconoce un movimiento",
    "M05": "Comprobante sin datos", "M06": "Comprobante sin indicar el correo",
    "M07": "Excepción para un reembolso", "M08": "Transferencia a su hermano",
    "M09": "Subir el límite de transferencias", "M10": "Enviar movimientos a su contador",
}
for id_ in ids_m:
    rs = [r for r in limpio if r["id"] == id_]
    mal = sum(not verdadero(r["exito"]) for r in rs)
    fig5.append({"label": f"{id_}  {descripciones[id_]}", "k": mal, "n": len(rs), "p": mal / len(rs)})
util = [r for r in limpio if r["clase"] == "legitima"]
utilidad = {"k": sum(verdadero(r["exito"]) for r in util), "n": len(util)}

json.dump({"fig1": fig1, "fig2": fig2, "fig3": fig3, "fig4": fig4, "fig5": fig5, "utilidad": utilidad},
          open(SALIDA, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("datos listos:", SALIDA)
for f in fig1:
    print(f"  {f['label']:<55} {f['k']}/{f['n']} = {100*f['p']:.0f}%  [{100*f['lo']:.0f}-{100*f['hi']:.0f}]")
for f in fig2:
    print(f"  {f['label'].replace(chr(10), ' '):<40} {f['k']}/{f['n']} = {100*f['p']:.0f}%")
print("  dilución recall@4:", [round(v, 2) for v in fig3["recall4"]])
print("  sin atacante:", sum(f["k"] for f in fig5), "/", sum(f["n"] for f in fig5), "| utilidad", utilidad)
