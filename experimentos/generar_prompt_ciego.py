"""Genera, UNA sola vez, el prompt del perfil `endurecido-ciego`.

El modelo configurado en la app redacta las instrucciones de sistema a partir de una consigna neutra que
no menciona ningún ataque (ver experimento.PromptCiegoController). El resultado se guarda en
experimentos/prompts/endurecido-ciego.txt, junto con un .meta.json (modelo, fecha, consigna).

Para no elegir a mano un prompt que convenga, el script se NIEGA a sobrescribir un archivo existente.
Si se quiere un prompt escrito por otra persona o por otro modelo, copiarlo a ese archivo a mano y anotarlo
en el .meta.json.

    python experimentos/generar_prompt_ciego.py
"""
import json
import sys
from datetime import datetime
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")
sys.path.insert(0, str(Path(__file__).parent))
import run_attacks as ra  # noqa: E402

destino = Path(__file__).parent / "prompts" / "endurecido-ciego.txt"
meta = destino.with_suffix(".meta.json")
if destino.exists():
    sys.exit(f"Ya existe {destino}. No se regenera (evita elegir el prompt a conveniencia).")

ra.esperar_base_lista()
r = ra.http("GET", "/experiment/prompt-ciego/generar", timeout=600)
if not r["prompt"].strip():
    sys.exit("El modelo no devolvió texto.")
destino.parent.mkdir(parents=True, exist_ok=True)
destino.write_text(r["prompt"] + "\n", encoding="utf-8")
meta.write_text(json.dumps({"generado_por": r["modelo"], "fecha": datetime.now().isoformat(timespec="seconds"),
                            "consigna": r["consigna"]}, ensure_ascii=False, indent=2), encoding="utf-8")
print(f"Generado por {r['modelo']} -> {destino}\n")
print(r["prompt"])
