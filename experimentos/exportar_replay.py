"""Convierte una corrida (CSV + .trazas.jsonl) al formato que consume el re-evaluador de PromptGuard.

El re-evaluador (promptguard: `gradlew replay`) ejecuta el MISMO código Java del guard sobre las llamadas a
herramientas que hizo el modelo en una corrida, con otras configuraciones (ablación de reglas, C4 refinada), sin volver
a llamar al modelo.

Requisito: la corrida debe ser en modo sombra (promptguard.modo=shadow) o sin PromptGuard, para que todas las llamadas
del modelo se hayan ejecutado y queden registradas. En enforce las llamadas bloqueadas no figuran en la traza.

Uso:  python experimentos/exportar_replay.py experimentos/resultados/<corrida>.csv
Salida: <corrida>.replay.json junto al CSV.
"""
import csv
import json
import re
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")
sys.path.insert(0, str(Path(__file__).parent))
import run_attacks as ra  # noqa: E402

SEPARADOR_ARGS = re.compile(r", (?=[A-Za-z_]\w*=)")


def parsear_args(texto):
    args = {}
    for par in SEPARADOR_ARGS.split(texto or ""):
        if "=" in par:
            clave, valor = par.split("=", 1)
            args[clave.strip()] = valor.strip()
    return args


def main():
    csv_path = Path(sys.argv[1])
    filas = list(csv.DictReader(open(csv_path, encoding="utf-8")))
    trazas = [json.loads(l) for l in open(csv_path.with_suffix(".trazas.jsonl"), encoding="utf-8")]

    ids = {}
    for lista in (ra.ATAQUES, ra.LEGITIMAS, ra.AMBIGUAS):
        for id_, _tipo, pregunta, *_ in lista:
            ids[pregunta] = id_

    # Un caso empieza en cada traza cuya pregunta está en el catálogo; la traza del turno 2 se suma a su caso.
    instancias = []
    for t in trazas:
        if t["pregunta"] in ids:
            instancias.append({"id": ids[t["pregunta"]], "trazas": [t]})
        elif instancias:
            instancias[-1]["trazas"].append(t)

    if len(instancias) != len(filas):
        sys.exit(f"Las trazas ({len(instancias)} casos) no coinciden con el CSV ({len(filas)} filas).")

    casos = []
    for fila, inst in zip(filas, instancias):
        if fila["id"] != inst["id"]:
            sys.exit(f"Orden distinto: CSV {fila['id']} frente a trazas {inst['id']}")
        mensajes = [t["pregunta"] for t in inst["trazas"]]
        fragmentos, vistos = [], set()
        for t in inst["trazas"]:
            for d in t.get("documentosRecuperados", []):
                clave = (d["fuente"], d["origen"])
                if clave not in vistos:
                    vistos.add(clave)
                    fragmentos.append({"fuente": d["fuente"], "origen": d["origen"]})
        llamadas = [{"herramienta": h["herramienta"], "args": parsear_args(h["argumentos"]),
                     "resultado": h.get("resultado") or ""}
                    for t in inst["trazas"] for h in t.get("herramientas", [])]
        casos.append({"id": fila["id"], "clase": fila["clase"], "exito": fila["exito"] == "True",
                      "mensajes": mensajes, "fragmentos": fragmentos, "llamadas": llamadas})

    salida = csv_path.with_suffix(".replay.json")
    salida.write_text(json.dumps({"corrida": csv_path.name, "casos": casos}, ensure_ascii=False, indent=1),
                      encoding="utf-8")
    print(f"{len(casos)} casos, {sum(len(c['llamadas']) for c in casos)} llamadas -> {salida}")


if __name__ == "__main__":
    main()
