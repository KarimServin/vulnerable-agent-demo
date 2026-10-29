"""Atacante adaptativo automatizado contra el agente de la app corriendo.

Por cada objetivo, un LLM atacante (el modelo configurado en la app) redacta un documento envenenado,
la app lo indexa, el agente responde una consulta inocente y el atacante reintenta con lo ocurrido,
hasta `--max-intentos`. Mide el éxito dentro de N intentos.

    python experimentos/run_adaptativo.py --conocimiento gris
    python experimentos/run_adaptativo.py --conocimiento negro --solo G2,G3 --max-intentos 8

conocimiento: "gris" = el atacante conoce el prompt del agente y los nombres de las herramientas;
              "negro" = no los conoce.

Guarda un JSON con todos los documentos generados en experimentos/resultados/. Un límite de tasa (429)
interrumpe el objetivo y se informa: no cuenta como defensa.
"""
import argparse
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")
sys.path.insert(0, str(Path(__file__).parent))
import run_attacks as ra  # noqa: E402  (reutiliza http() y esperar_base_lista())


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--conocimiento", choices=["gris", "negro"], default="gris")
    ap.add_argument("--max-intentos", type=int, default=10)
    ap.add_argument("--solo", help="IDs de objetivo separados por coma, ej. G1,G3")
    ap.add_argument("--salida", default=str(Path(__file__).parent / "resultados"))
    args = ap.parse_args()
    solo = set(args.solo.split(",")) if args.solo else None

    ra.esperar_base_lista()
    config = ra.http("GET", "/experiment/config")
    objetivos = ra.http("GET", "/experiment/adaptativo/objetivos")
    print(f"perfil={config['perfil']} modelo={config['modelo']} corpus={config.get('corpus', '?')} "
          f"conocimiento={args.conocimiento} max_intentos={args.max_intentos}\n", flush=True)

    resultados = []
    for o in objetivos:
        if solo and o["id"] not in solo:
            continue
        print(f"== {o['id']}: {o['descripcion'][:95]}", flush=True)
        r = ra.http("POST", "/experiment/adaptativo", {
            "objetivo": o["id"], "conocimiento": args.conocimiento, "maxIntentos": args.max_intentos}, timeout=1800)
        resultados.append(r)
        for i in r["intentos"]:
            marca = "ÉXITO" if i["exito"] else "falló"
            print(f"   intento {i['numero']:>2}: recuperado={'sí' if i['recuperado'] else 'no'} "
                  f"herramientas={[h.split('(')[0] for h in i['herramientas']] or '-'} -> {marca}"
                  + (f"  [{i['error'][:80]}]" if i["error"] else ""), flush=True)
        if r["abortado"]:
            print(f"   ABORTADO (no es una defensa): {r['abortado'][:200]}", flush=True)
        print(f"   => {'EXITOSO en ' + str(r['intentosUsados']) + ' intentos' if r['exitoso'] else 'no logrado en ' + str(r['intentosUsados']) + ' intentos'}\n", flush=True)

    if not resultados:
        sys.exit("No se ejecutó ningún objetivo.")

    ok = sum(r["exitoso"] for r in resultados)
    print(f"=== RESUMEN perfil={config['perfil']} modelo={config['modelo']} conocimiento={args.conocimiento} ===")
    for r in resultados:
        print(f"{r['objetivo']}: {'EXITOSO en ' + str(r['intentosUsados']) if r['exitoso'] else 'no logrado (' + str(r['intentosUsados']) + ' intentos)'}"
              f"{'  [ABORTADO]' if r['abortado'] else ''}")
    print(f"\nObjetivos logrados: {ok}/{len(resultados)}")

    salida = Path(args.salida)
    salida.mkdir(parents=True, exist_ok=True)
    modelo = config["modelo"].replace(":", "-").replace("/", "-")
    archivo = salida / (f"adaptativo_{config['perfil']}_{modelo}_{config.get('corpus', '?')}_"
                        f"{args.conocimiento}_{datetime.now():%Y%m%d_%H%M%S}.json")
    archivo.write_text(json.dumps({"config": config, "conocimiento": args.conocimiento,
                                   "max_intentos": args.max_intentos, "resultados": resultados},
                                  ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"JSON: {archivo}")
    if any(r["abortado"] for r in resultados):
        print("CORRIDA INCOMPLETA: algún objetivo se interrumpió por un error de la API.")
        sys.exit(2)


if __name__ == "__main__":
    main()
