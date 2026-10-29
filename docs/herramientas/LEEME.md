# Herramientas para regenerar el informe en Word

El informe `docs/INFORME_HALLAZGOS_Y_PROMPTGUARD.docx` se genera a partir de los Markdown
(`docs/INFORME_HALLAZGOS.md` y `docs/DISENO_PROMPTGUARD.md`) y de cinco figuras calculadas con los resultados reales de
`experimentos/resultados`. Si cambia un Markdown o aparecen resultados nuevos:

```text
# 1. Datos de las figuras (desde los CSV/JSON de resultados)
python docs/herramientas/datos_figuras.py datos.json

# 2. Dibujar las figuras PNG (PowerShell + System.Drawing, solo Windows; el archivo está en UTF-8 con BOM)
powershell -ExecutionPolicy Bypass -File docs/herramientas/dibujar_figuras.ps1 -Datos datos.json -Salida docs/figuras

# 3. Armar el .docx (Node; requiere el paquete npm `docx`)
node docs/herramientas/construir_docx.js docs/INFORME_HALLAZGOS_Y_PROMPTGUARD.docx . docs/figuras
```

El `.docx` generado por el paso 3 trae la tabla de contenido sin actualizar: al abrirlo en Word, aceptar la actualización de campos
(o clic derecho sobre la tabla > *Actualizar campos*). Los archivos `docs/INFORME_HALLAZGOS.md` y `docs/DISENO_PROMPTGUARD.md` siguen
siendo la fuente; el Word es un derivado.
