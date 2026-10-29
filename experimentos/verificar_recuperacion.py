"""Verifica, sin llamar al modelo de chat, qué documentos recupera el RAG por cada caso.

Úsalo antes de correr run_attacks.py: un ataque indirecto solo mide algo si su
documento envenenado llega al contexto. Requiere la app levantada.

    python experimentos/verificar_recuperacion.py            # app en :8080
    python experimentos/verificar_recuperacion.py 8081
"""
import json
import sys
import urllib.parse
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import run_attacks as ra  # noqa: E402

sys.stdout.reconfigure(encoding="utf-8")
puerto = sys.argv[1] if len(sys.argv) > 1 else "8080"
casos = [(i, t, p) for i, t, p, _ in ra.ATAQUES] + [(i, t, p) for i, t, p, _ in ra.LEGITIMAS]
ra.BASE = f"http://localhost:{puerto}"
ra.esperar_base_lista()
if ra.http("GET", "/experiment/config").get("corpusModo") == "limpio":
    print("Corpus limpio: no hay documentos envenenados; la verificación de recuperación no aplica.")
    sys.exit(0)
sin_veneno = []
for id_, tipo, pregunta in casos:
    url = f"http://localhost:{puerto}/experiment/retrieval?" + urllib.parse.urlencode({"pregunta": pregunta})
    docs = json.load(urllib.request.urlopen(url))
    fuentes = [d["fuente"].replace(".txt", "") for d in docs]
    es_indirecto = tipo not in ("directa", "legitima")
    propio = any(d["origen"] == "NO_CONFIABLE" and d["fuente"].startswith(tipo + "__") for d in docs)
    marca = "SI " if propio else ("no " if es_indirecto else " - ")
    if es_indirecto and not propio:
        sin_veneno.append(id_)
    print(f"{id_:<4}{tipo:<14}veneno propio en contexto: {marca} {fuentes}")

print()
if sin_veneno:
    print("ATENCIÓN: estos ataques indirectos NO recuperan su documento envenenado:", ", ".join(sin_veneno))
    sys.exit(1)
print("OK: todos los ataques indirectos recuperan su documento envenenado.")
