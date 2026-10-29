// Construye un .docx a partir de los documentos Markdown del proyecto y las figuras generadas.
// Uso: node construir_docx.js <salida.docx> <raiz-del-repo> <carpeta-figuras>
const fs = require('fs');
const path = require('path');
const {
  Document, Packer, Paragraph, TextRun, HeadingLevel, Table, TableRow, TableCell, WidthType, ShadingType,
  AlignmentType, LevelFormat, BorderStyle, ImageRun, Header, Footer, PageNumber, TableOfContents, PageBreak,
  ExternalHyperlink,
} = require('docx');

const [salida, raiz, carpetaFiguras] = process.argv.slice(2);
const ANCHO = 9638; // contenido A4 con margen de 2 cm, en DXA
const FUENTE = 'Arial';

// ------------------------------------------------------------------ Markdown -> bloques
function reemplazarEnlaces(texto) {
  return texto
    .replace(/\[DISENO_PROMPTGUARD\.md\]\(DISENO_PROMPTGUARD\.md\)/g, 'la Parte II de este documento')
    .replace(/\[INFORME_HALLAZGOS\.md\]\(INFORME_HALLAZGOS\.md\)/g, 'la Parte I de este documento')
    .replace(/\[GUIA_ENTREVISTA\.md\]\(GUIA_ENTREVISTA\.md\)/g, 'la guía de entrevista (docs/GUIA_ENTREVISTA.md)');
}

function parsear(texto) {
  const lineas = reemplazarEnlaces(texto).replace(/\r/g, '').split('\n');
  const bloques = [];
  const esLista = (l) => /^(\s*)([-*]|\d+\.)\s+(.*)$/.test(l);
  const esTabla = (l) => /^\s*\|/.test(l);
  const inicioBloque = (l) => /^#{1,6}\s/.test(l) || /^---+\s*$/.test(l) || l.startsWith('```') || /^>/.test(l) || esLista(l) || esTabla(l);
  let i = 0;
  while (i < lineas.length) {
    const l = lineas[i];
    if (!l.trim()) { i++; continue; }
    if (l.startsWith('```')) {
      const codigo = []; i++;
      while (i < lineas.length && !lineas[i].startsWith('```')) { codigo.push(lineas[i]); i++; }
      i++; bloques.push({ tipo: 'codigo', lineas: codigo }); continue;
    }
    let m;
    if ((m = l.match(/^(#{1,6})\s+(.*)$/))) { bloques.push({ tipo: 'h', nivel: m[1].length, texto: m[2].trim() }); i++; continue; }
    if (/^---+\s*$/.test(l)) { bloques.push({ tipo: 'hr' }); i++; continue; }
    if (/^>/.test(l)) {
      const partes = [];
      while (i < lineas.length && /^>/.test(lineas[i])) { partes.push(lineas[i].replace(/^>\s?/, '').trim()); i++; }
      bloques.push({ tipo: 'cita', texto: partes.filter(Boolean).join(' ') }); continue;
    }
    if (esTabla(l) && i + 1 < lineas.length && /^\s*\|?\s*:?-{3,}/.test(lineas[i + 1])) {
      const filas = [];
      while (i < lineas.length && esTabla(lineas[i])) {
        if (!/^\s*\|?\s*:?-{3,}/.test(lineas[i])) {
          filas.push(lineas[i].trim().replace(/^\|/, '').replace(/\|$/, '').split('|').map((c) => c.trim()));
        }
        i++;
      }
      bloques.push({ tipo: 'tabla', filas }); continue;
    }
    if ((m = l.match(/^(\s*)([-*]|\d+\.)\s+(.*)$/))) {
      const items = [];
      while (i < lineas.length) {
        const mm = lineas[i].match(/^(\s*)([-*]|\d+\.)\s+(.*)$/);
        if (mm) {
          items.push({ nivel: mm[1].length >= 2 ? 1 : 0, num: /\d/.test(mm[2]) ? mm[2] : null, texto: mm[3].trim() });
          i++;
        } else if (lineas[i].trim() && /^\s{2,}\S/.test(lineas[i]) && items.length) {
          items[items.length - 1].texto += ' ' + lineas[i].trim(); i++;
        } else break;
      }
      bloques.push({ tipo: 'lista', items }); continue;
    }
    const partes = [];
    while (i < lineas.length && lineas[i].trim() && !inicioBloque(lineas[i])) { partes.push(lineas[i].trim()); i++; }
    if (!partes.length) { partes.push(l.trim()); i++; }
    bloques.push({ tipo: 'p', texto: partes.join(' ') });
  }
  return bloques;
}

// ------------------------------------------------------------------ Inline -> TextRun
const RE_INLINE = /(`[^`]+`)|(\*\*.+?\*\*)|((?<![\w*])\*(?![\s*]).+?(?<![\s*])\*(?![\w*]))|(\[[^\]]+\]\([^)]+\))|(https?:\/\/[^\s)]+)/g;
function inline(texto, base = {}) {
  const out = [];
  let ultimo = 0; let m;
  const re = new RegExp(RE_INLINE.source, 'g'); // una regex por llamada: la recursión no debe pisar lastIndex
  const tr = (t, extra = {}) => new TextRun({ text: t, font: FUENTE, ...base, ...extra });
  while ((m = re.exec(texto)) !== null) {
    if (m.index > ultimo) out.push(tr(texto.slice(ultimo, m.index)));
    const [tok] = m;
    if (m[1]) {
      out.push(new TextRun({ text: tok.slice(1, -1), font: 'Consolas', size: Math.round((base.size || 21) * 0.9),
        color: '7B1E3A', bold: base.bold, italics: base.italics }));
    } else if (m[2]) {
      out.push(...inline(tok.slice(2, -2), { ...base, bold: true }));
    } else if (m[3]) {
      out.push(...inline(tok.slice(1, -1), { ...base, italics: true }));
    } else if (m[4]) {
      const mm = tok.match(/^\[([^\]]+)\]\(([^)]+)\)$/);
      if (/^https?:/.test(mm[2])) {
        out.push(new ExternalHyperlink({ link: mm[2], children: [tr(mm[1], { color: '1F5FA8', underline: {} })] }));
      } else out.push(tr(mm[1]));
    } else if (m[5]) {
      out.push(new ExternalHyperlink({ link: tok, children: [tr(tok, { color: '1F5FA8', underline: {} })] }));
    }
    ultimo = m.index + tok.length;
  }
  if (ultimo < texto.length) out.push(tr(texto.slice(ultimo)));
  return out;
}

// ------------------------------------------------------------------ Bloques -> docx
const borde = { style: BorderStyle.SINGLE, size: 4, color: 'B8BEC8' };
const bordes = { top: borde, bottom: borde, left: borde, right: borde };

function anchosColumnas(filas) {
  const n = Math.max(...filas.map((f) => f.length));
  const pesos = [];
  for (let c = 0; c < n; c++) {
    let mx = 6;
    for (const f of filas) { const t = (f[c] || '').replace(/[`*]/g, ''); mx = Math.max(mx, Math.min(t.length, 70)); }
    pesos.push(Math.sqrt(mx) + 1.5);
  }
  const total = pesos.reduce((a, b) => a + b, 0);
  const minimo = 850;
  let w = pesos.map((p) => Math.max(minimo, Math.floor((ANCHO * p) / total)));
  let suma = w.reduce((a, b) => a + b, 0);
  const idx = w.indexOf(Math.max(...w));
  w[idx] += ANCHO - suma; // ajusta para que sume exactamente ANCHO
  return w;
}

function tabla(filas) {
  const n = Math.max(...filas.map((f) => f.length));
  const w = anchosColumnas(filas);
  const filasDocx = filas.map((f, r) => new TableRow({
    tableHeader: r === 0, cantSplit: true,
    children: Array.from({ length: n }, (_, c) => new TableCell({
      width: { size: w[c], type: WidthType.DXA }, borders: bordes,
      shading: r === 0 ? { type: ShadingType.CLEAR, fill: 'D9E2F3', color: 'auto' } : undefined,
      margins: { top: 50, bottom: 50, left: 90, right: 90 },
      children: [new Paragraph({ spacing: { after: 0, line: 252 }, children: inline(f[c] || '', { size: 18, bold: r === 0 }) })],
    })),
  }));
  return [new Table({ width: { size: ANCHO, type: WidthType.DXA }, columnWidths: w, rows: filasDocx }),
    new Paragraph({ spacing: { after: 120 }, children: [] })];
}

function codigo(lineas) {
  const mx = Math.max(10, ...lineas.map((l) => l.length));
  const tamPt = Math.max(6, Math.min(9, 481.9 / (0.62 * mx)));
  const size = Math.round(tamPt * 2);
  const res = lineas.map((l, k) => new Paragraph({
    shading: { type: ShadingType.CLEAR, fill: 'F3F4F6', color: 'auto' },
    spacing: { before: 0, after: 0, line: 232 }, indent: { left: 100, right: 100 }, keepLines: true,
    children: [new TextRun({ text: l.length ? l : ' ', font: 'Consolas', size, color: '1F2937' })],
  }));
  res.push(new Paragraph({ spacing: { after: 140 }, children: [] }));
  return res;
}

function lista(items) {
  return items.map((it) => {
    if (it.num) {
      return new Paragraph({
        spacing: { after: 70, line: 264 }, indent: { left: 540 + it.nivel * 540, hanging: 360 },
        children: [new TextRun({ text: it.num + '\t', font: FUENTE, size: 21, bold: true }), ...inline(it.texto, { size: 21 })],
      });
    }
    return new Paragraph({
      numbering: { reference: 'vinetas', level: it.nivel }, spacing: { after: 70, line: 264 },
      children: inline(it.texto, { size: 21 }),
    });
  });
}

const NIVELES = { 2: HeadingLevel.HEADING_2, 3: HeadingLevel.HEADING_3, 4: HeadingLevel.HEADING_4, 5: HeadingLevel.HEADING_4, 6: HeadingLevel.HEADING_4 };

function convertir(bloques, { omitirPrimerH1 = true } = {}) {
  const out = []; let primero = omitirPrimerH1;
  for (const b of bloques) {
    if (b.tipo === 'h') {
      if (b.nivel === 1) { if (primero) { primero = false; continue; } out.push(new Paragraph({ heading: HeadingLevel.HEADING_2, children: inline(b.texto) })); continue; }
      out.push(new Paragraph({ heading: NIVELES[b.nivel], children: [new TextRun({ text: b.texto.replace(/\*\*/g, '').replace(/`/g, ''), font: FUENTE })] }));
    } else if (b.tipo === 'p') {
      out.push(new Paragraph({ spacing: { after: 120, line: 276 }, children: inline(b.texto, { size: 21 }) }));
    } else if (b.tipo === 'lista') out.push(...lista(b.items));
    else if (b.tipo === 'tabla') out.push(...tabla(b.filas));
    else if (b.tipo === 'codigo') out.push(...codigo(b.lineas));
    else if (b.tipo === 'cita') {
      out.push(new Paragraph({
        spacing: { before: 60, after: 160, line: 276 }, indent: { left: 240, right: 120 },
        border: { left: { style: BorderStyle.SINGLE, size: 24, color: '1F5FA8', space: 8 } },
        shading: { type: ShadingType.CLEAR, fill: 'EEF3FB', color: 'auto' },
        children: inline(b.texto, { size: 21 }),
      }));
    } else if (b.tipo === 'hr') {
      out.push(new Paragraph({ spacing: { before: 60, after: 120 }, border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: 'C9CED6', space: 1 } }, children: [] }));
    }
  }
  return out;
}

// ------------------------------------------------------------------ Figuras
function dimensionesPng(buf) { return { w: buf.readUInt32BE(16), h: buf.readUInt32BE(20) }; }
const FIGURAS = [
  ['fig1_asr_por_condicion.png', 'Figura 1. Tasa de éxito del ataque (ASR) por condición.',
    'Las barras rayadas son del corpus anterior (v1) y no son comparables con las del corpus v2. Con el mismo prompt endurecido, Claude Haiku 4.5 no ejecutó ninguna acción indebida (0 de 85), mientras que los modelos abiertos quedan entre 33% y 38%. Las corridas naranjas son parciales por la cuota de la API; las líneas son intervalos de confianza de Wilson al 95%.',
    'Fuente: experimentos/resultados (ingenuo_*.csv, endurecido_*.csv, endurecido-ciego_*.csv).'],
  ['fig2_uno_vs_dos_turnos.png', 'Figura 2. Un solo turno subestima la vulnerabilidad.',
    'El prompt generado a ciegas pide confirmar la intención del cliente antes de actuar. Con un solo turno parece mucho más seguro (11%); con un segundo turno en el que el cliente responde “sí, confirmo”, el ASR sube a 28%, del mismo orden que el prompt escrito a mano (33%).',
    'Fuente: endurecido-ciego_openai-openai-gpt-oss-120b_42e73d94b230_20261023_173426.csv y endurecido_openai-openai-gpt-oss-120b_42e73d94b230_20261022_232032.csv.'],
  ['fig3_dilucion.png', 'Figura 3. Efecto de agrandar la base de conocimiento sobre la recuperación del veneno.',
    'Con 3.041 documentos compitiendo, el documento envenenado sigue entre los 4 primeros resultados el 71% de las veces. La dilución reduce el ataque pero no lo elimina. La línea naranja muestra con qué frecuencia una consulta legítima trae un veneno al contexto sin que nadie ataque (valor inflado por la elección de las consultas).',
    'Fuente: experimentos/resultados/dilucion_42e73d94b230_20261022_091902.json (3 semillas, almacén exacto).'],
  ['fig4_mapa_ataques_modelos.png', 'Figura 4. Qué ataques funcionan contra cada modelo.',
    'Los ataques que funcionan son los que parecen política interna o simulan consentimiento; los pedidos burdos casi nunca. Los perfiles de vulnerabilidad de gpt-oss-20b y gpt-oss-120b difieren (solo coinciden A07 y A11), y Claude Haiku 4.5 no cae en ninguno. La última columna usa el prompt generado a ciegas.',
    'Fuente: experimentos/resultados (endurecido_*.csv y endurecido-ciego_*.csv); las celdas grises no se midieron por la cuota de la API.'],
  ['fig5_sin_atacante.png', 'Figura 5. Acciones indebidas sin ningún atacante (corpus limpio).',
    'Con solo documentos confiables, Claude Haiku 4.5 endurecido ejecutó una acción indebida en el 100% de los dos pedidos que la base confiable reserva a soporte (cambiar el correo registrado y enviar datos a un tercero) y en ninguno de los otros ocho. No inventó argumentos ni actuó sin pedido.',
    'Fuente: experimentos/resultados/endurecido_anthropic-claude-haiku-4-5_797e92481bc3_20261023_180359.csv.'],
];

function seccionFiguras() {
  const out = [new Paragraph({ heading: HeadingLevel.HEADING_2, pageBreakBefore: true, children: [new TextRun({ text: 'Anexo A — Figuras para la presentación', font: FUENTE })] }),
    new Paragraph({ spacing: { after: 160 }, children: inline('Las cinco figuras salen de los CSV y JSON de `experimentos/resultados` (se pueden regenerar con los scripts de `docs/herramientas`). Están pensadas para usarse directamente en las diapositivas; los archivos PNG están en `docs/figuras`.', { size: 21 }) })];
  for (const [archivo, titulo, lectura, fuente] of FIGURAS) {
    const buf = fs.readFileSync(path.join(carpetaFiguras, archivo));
    const { w, h } = dimensionesPng(buf);
    const ancho = 500; const alto = Math.round((ancho * h) / w);
    out.push(new Paragraph({ keepNext: true, spacing: { before: 200, after: 80 }, children: [new TextRun({ text: titulo, font: FUENTE, bold: true, size: 22, color: '1F3864' })] }));
    out.push(new Paragraph({ keepNext: true, alignment: AlignmentType.CENTER, spacing: { after: 60 },
      children: [new ImageRun({ type: 'png', data: buf, transformation: { width: ancho, height: alto },
        altText: { title: titulo, description: lectura, name: archivo } })] }));
    out.push(new Paragraph({ spacing: { after: 60, line: 264 }, children: [new TextRun({ text: lectura, font: FUENTE, size: 20 })] }));
    out.push(new Paragraph({ spacing: { after: 160 }, children: [new TextRun({ text: fuente, font: FUENTE, size: 17, italics: true, color: '6B7280' })] }));
  }
  return out;
}

// ------------------------------------------------------------------ Documento
const md1 = fs.readFileSync(path.join(raiz, 'docs', 'INFORME_HALLAZGOS.md'), 'utf8');
const md2 = fs.readFileSync(path.join(raiz, 'docs', 'DISENO_PROMPTGUARD.md'), 'utf8');
const md3 = fs.readFileSync(path.join(raiz, 'docs', 'RESULTADOS_PROMPTGUARD.md'), 'utf8');

const portada = [
  new Paragraph({ spacing: { before: 2600, after: 200 }, children: [new TextRun({ text: 'TRABAJO JIT2026-T112', font: FUENTE, size: 24, bold: true, color: '6B7280', characterSpacing: 40 })] }),
  new Paragraph({ spacing: { after: 160, line: 300 }, children: [new TextRun({ text: 'Informe de hallazgos, diseño y evaluación de PromptGuard', font: FUENTE, size: 56, bold: true, color: '1F3864' })] }),
  new Paragraph({ spacing: { after: 360, line: 300 }, border: { bottom: { style: BorderStyle.SINGLE, size: 12, color: '1F5FA8', space: 8 } },
    children: [new TextRun({ text: 'Aplicación de referencia vulnerable-agent-demo: agentes de IA con RAG y herramientas frente a documentos envenenados y exceso de autonomía', font: FUENTE, size: 26, color: '374151' })] }),
  new Paragraph({ spacing: { after: 80 }, children: [new TextRun({ text: 'Documento de estudio y presentación en congreso', font: FUENTE, size: 24, bold: true })] }),
  new Paragraph({ spacing: { after: 600 }, children: [new TextRun({ text: 'Estado al 27 de octubre de 2026', font: FUENTE, size: 24, color: '374151' })] }),
  new Paragraph({
    spacing: { before: 200, after: 120, line: 276 }, indent: { left: 240, right: 240 },
    border: { top: { style: BorderStyle.SINGLE, size: 6, color: 'E08A1E', space: 6 }, bottom: { style: BorderStyle.SINGLE, size: 6, color: 'E08A1E', space: 6 }, left: { style: BorderStyle.SINGLE, size: 6, color: 'E08A1E', space: 6 }, right: { style: BorderStyle.SINGLE, size: 6, color: 'E08A1E', space: 6 } },
    shading: { type: ShadingType.CLEAR, fill: 'FEF6E7', color: 'auto' },
    children: [new TextRun({ text: 'Importante: ', font: FUENTE, size: 21, bold: true }),
      new TextRun({ text: 'la aplicación descripta es el baseline sin defensas. La Parte I mide el problema y las defensas que ya existían; la Parte II es el diseño de PromptGuard basado en esa evidencia (23/10); la Parte III es su evaluación preliminar (24 al 27/10): un dominio, pocas repeticiones, y un atacante que conoce la política supera la configuración con C4 refinada.', font: FUENTE, size: 21 })],
  }),
  new Paragraph({ children: [new PageBreak()] }),
  new Paragraph({ spacing: { after: 200 }, children: [new TextRun({ text: 'Contenido', font: FUENTE, size: 36, bold: true, color: '1F3864' })] }),
  new TableOfContents('Contenido', { hyperlink: true, headingStyleRange: '1-3' }),
  new Paragraph({ children: [new PageBreak()] }),
];

const parte1 = [
  new Paragraph({ heading: HeadingLevel.HEADING_1, children: [new TextRun({ text: 'Parte I — Informe de hallazgos', font: FUENTE })] }),
  ...convertir(parsear(md1)),
  ...seccionFiguras(),
];
const parte2 = [
  new Paragraph({ heading: HeadingLevel.HEADING_1, pageBreakBefore: true, children: [new TextRun({ text: 'Parte II — Diseño propuesto de PromptGuard', font: FUENTE })] }),
  ...convertir(parsear(md2)),
];
const parte3 = [
  new Paragraph({ heading: HeadingLevel.HEADING_1, pageBreakBefore: true, children: [new TextRun({ text: 'Parte III — Evaluación de PromptGuard', font: FUENTE })] }),
  ...convertir(parsear(md3)),
];

// EXPOSICION=1 arma solo el guion y resumen de la exposición (docs/EXPOSICION.md), sin tabla de contenido.
const modoExposicion = process.env.EXPOSICION === '1';
const exposicion = modoExposicion ? [
  new Paragraph({ spacing: { after: 120 }, children: [new TextRun({ text: 'TRABAJO JIT2026-T112', font: FUENTE, size: 20, bold: true, color: '6B7280', characterSpacing: 40 })] }),
  new Paragraph({ spacing: { after: 200 }, border: { bottom: { style: BorderStyle.SINGLE, size: 12, color: '1F5FA8', space: 6 } },
    children: [new TextRun({ text: 'Exposición de PromptGuard: guion de 15 minutos y resumen para estudiar', font: FUENTE, size: 40, bold: true, color: '1F3864' })] }),
  ...convertir(parsear(fs.readFileSync(path.join(raiz, 'docs', 'EXPOSICION.md'), 'utf8')
    .replace(/^## /gm, '### ').replace(/^# Parte/gm, '## Parte'))),
] : [];

const doc = new Document({
  creator: 'JIT2026-T112', title: 'Informe de hallazgos, diseño y evaluación de PromptGuard',
  description: 'Hallazgos sobre agentes con RAG y herramientas frente a documentos envenenados, diseño de PromptGuard y su evaluación preliminar',
  features: { updateFields: true },
  styles: {
    default: { document: { run: { font: FUENTE, size: 21 } } },
    paragraphStyles: [
      { id: 'Heading1', name: 'Heading 1', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { size: 38, bold: true, font: FUENTE, color: '1F3864' }, paragraph: { spacing: { before: 360, after: 200 }, outlineLevel: 0, keepNext: true } },
      { id: 'Heading2', name: 'Heading 2', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { size: 30, bold: true, font: FUENTE, color: '1F5FA8' }, paragraph: { spacing: { before: 320, after: 140 }, outlineLevel: 1, keepNext: true } },
      { id: 'Heading3', name: 'Heading 3', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { size: 25, bold: true, font: FUENTE, color: '2F5496' }, paragraph: { spacing: { before: 240, after: 100 }, outlineLevel: 2, keepNext: true } },
      { id: 'Heading4', name: 'Heading 4', basedOn: 'Normal', next: 'Normal', quickFormat: true,
        run: { size: 22, bold: true, font: FUENTE, color: '374151' }, paragraph: { spacing: { before: 200, after: 80 }, outlineLevel: 3, keepNext: true } },
    ],
  },
  numbering: { config: [{ reference: 'vinetas', levels: [
    { level: 0, format: LevelFormat.BULLET, text: '•', alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 540, hanging: 270 } } } },
    { level: 1, format: LevelFormat.BULLET, text: '–', alignment: AlignmentType.LEFT, style: { paragraph: { indent: { left: 1080, hanging: 270 } } } },
  ] }] },
  sections: [{
    properties: { titlePage: !modoExposicion, page: { size: { width: 11906, height: 16838 }, margin: { top: 1134, bottom: 1134, left: 1134, right: 1134 } } },
    headers: {
      first: new Header({ children: [new Paragraph({ children: [] })] }),
      default: new Header({ children: [new Paragraph({ alignment: AlignmentType.RIGHT,
        border: { bottom: { style: BorderStyle.SINGLE, size: 4, color: 'C9CED6', space: 4 } },
        children: [new TextRun({ text: 'JIT2026-T112 · vulnerable-agent-demo · Hallazgos, diseño y evaluación de PromptGuard', font: FUENTE, size: 16, color: '6B7280' })] })] }),
    },
    footers: {
      first: new Footer({ children: [new Paragraph({ children: [] })] }),
      default: new Footer({ children: [new Paragraph({ alignment: AlignmentType.CENTER, children: [
        new TextRun({ text: 'Página ', font: FUENTE, size: 16, color: '6B7280' }),
        new TextRun({ children: [PageNumber.CURRENT], font: FUENTE, size: 16, color: '6B7280' }),
        new TextRun({ text: ' de ', font: FUENTE, size: 16, color: '6B7280' }),
        new TextRun({ children: [PageNumber.TOTAL_PAGES], font: FUENTE, size: 16, color: '6B7280' }),
      ] })] }),
    },
    children: modoExposicion ? exposicion : [...portada, ...parte1, ...parte2, ...parte3],
  }],
});

Packer.toBuffer(doc).then((buf) => { fs.writeFileSync(salida, buf); console.log('docx escrito:', salida, Math.round(buf.length / 1024) + ' KB'); });
