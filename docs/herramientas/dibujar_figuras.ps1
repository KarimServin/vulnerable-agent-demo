param([string]$Datos, [string]$Salida)
Add-Type -AssemblyName System.Drawing
$d = Get-Content -Raw -Encoding UTF8 $Datos | ConvertFrom-Json

function Col([string]$hex) { [System.Drawing.ColorTranslator]::FromHtml($hex) }
function Pincel($c) { New-Object System.Drawing.SolidBrush $c }
function Fuente([int]$px, [string]$estilo = 'Regular') {
    New-Object System.Drawing.Font('Segoe UI', $px, [System.Drawing.FontStyle]::$estilo, [System.Drawing.GraphicsUnit]::Pixel)
}
function Lienzo([int]$w, [int]$h) {
    $bmp = New-Object System.Drawing.Bitmap $w, $h
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = 'AntiAlias'
    $g.TextRenderingHint = 'AntiAliasGridFit'
    $g.Clear([System.Drawing.Color]::White)
    return @{ bmp = $bmp; g = $g }
}
function Texto($g, [string]$t, $fuente, $pincel, [float]$x, [float]$y, [string]$h = 'Near', [string]$v = 'Near') {
    $sf = New-Object System.Drawing.StringFormat
    $sf.Alignment = [System.Drawing.StringAlignment]::$h
    $sf.LineAlignment = [System.Drawing.StringAlignment]::$v
    $g.DrawString($t, $fuente, $pincel, [float]$x, [float]$y, $sf)
}
function Guardar($lienzo, [string]$nombre) {
    $ruta = Join-Path $Salida $nombre
    $lienzo.bmp.Save($ruta, [System.Drawing.Imaging.ImageFormat]::Png)
    $lienzo.g.Dispose(); $lienzo.bmp.Dispose()
    Write-Host "figura: $ruta"
}
function Pct($p) { '{0:N0}%' -f (100 * $p) }

$negro = Pincel (Col '#1F2937'); $gris = Pincel (Col '#6B7280'); $grisClaro = Col '#D1D5DB'
$azul = Col '#1F5FA8'; $naranja = Col '#E08A1E'; $rojo = Col '#B83227'; $verde = Col '#2E7D4F'; $grisAzul = Col '#8AA1BD'

# ---------------------------------------------------------------- Figura 1: ASR por condición
$n = $d.fig1.Count
$W = 1800; $filaH = 118; $H = 190 + $n * $filaH + 170
$L = Lienzo $W $H; $g = $L.g
Texto $g 'Tasa de éxito del ataque (ASR) por condición' (Fuente 40 'Bold') $negro 40 30
Texto $g 'Barras: proporción de ejecuciones con acción sensible ejecutada. Líneas: intervalo de confianza de Wilson al 95%.' (Fuente 24) $gris 40 88
$px0 = 830; $pw = 880; $py0 = 170
$lapiz = New-Object System.Drawing.Pen $grisClaro, 2
foreach ($t in 0, 25, 50, 75, 100) {
    $x = $px0 + $pw * $t / 100
    $g.DrawLine($lapiz, [float]$x, [float]$py0, [float]$x, [float]($py0 + $n * $filaH))
    Texto $g "$t%" (Fuente 22) $gris $x ($py0 + $n * $filaH + 8) 'Center'
}
for ($i = 0; $i -lt $n; $i++) {
    $f = $d.fig1[$i]; $y = $py0 + $i * $filaH
    Texto $g $f.label (Fuente 26) $negro ($px0 - 24) ($y + $filaH / 2) 'Far' 'Center'
    $bw = [float]($pw * $f.p); $bh = 54; $by = $y + ($filaH - $bh) / 2
    if ($f.estado -eq 'anterior') { $pin = New-Object System.Drawing.Drawing2D.HatchBrush('WideUpwardDiagonal', $grisAzul, [System.Drawing.Color]::White) }
    elseif ($f.estado -eq 'parcial') { $pin = Pincel $naranja }
    else { $pin = Pincel $azul }
    if ($bw -gt 0.5) { $g.FillRectangle($pin, [float]$px0, [float]$by, $bw, [float]$bh) }
    $g.DrawRectangle((New-Object System.Drawing.Pen (Col '#374151'), 2), [float]$px0, [float]$by, [float][Math]::Max($bw, 1), [float]$bh)
    $lx = $px0 + $pw * $f.lo; $hx = $px0 + $pw * $f.hi; $cy = $by + $bh / 2
    $pw2 = New-Object System.Drawing.Pen (Col '#111827'), 3
    $g.DrawLine($pw2, [float]$lx, [float]$cy, [float]$hx, [float]$cy)
    $g.DrawLine($pw2, [float]$lx, [float]($cy - 12), [float]$lx, [float]($cy + 12))
    $g.DrawLine($pw2, [float]$hx, [float]($cy - 12), [float]$hx, [float]($cy + 12))
    Texto $g ("{0}/{1} = {2}" -f $f.k, $f.n, (Pct $f.p)) (Fuente 26 'Bold') $negro ($hx + 16) $cy 'Near' 'Center'
}
# leyenda
$ly = $py0 + $n * $filaH + 70
$leyenda = @(@('Corpus anterior (v1): no comparable con v2', 'anterior'), @('Corrida parcial (cuota de la API)', 'parcial'), @('Corrida completa, corpus v2', 'completo'))
$lx0 = 40
foreach ($e in $leyenda) {
    if ($e[1] -eq 'anterior') { $pin = New-Object System.Drawing.Drawing2D.HatchBrush('WideUpwardDiagonal', $grisAzul, [System.Drawing.Color]::White) }
    elseif ($e[1] -eq 'parcial') { $pin = Pincel $naranja } else { $pin = Pincel $azul }
    $g.FillRectangle($pin, [float]$lx0, [float]$ly, 34.0, 34.0)
    $g.DrawRectangle((New-Object System.Drawing.Pen (Col '#374151'), 2), [float]$lx0, [float]$ly, 34.0, 34.0)
    Texto $g $e[0] (Fuente 24) $negro ($lx0 + 46) ($ly + 17) 'Near' 'Center'
    $lx0 += 600
}
Guardar $L 'fig1_asr_por_condicion.png'

# ---------------------------------------------------------------- Figura 2: uno contra dos turnos
$W = 1500; $H = 1000
$L = Lienzo $W $H; $g = $L.g
Texto $g 'Un solo turno subestima la vulnerabilidad' (Fuente 40 'Bold') $negro 40 30
Texto $g 'gpt-oss-120b, ataques A01–A12 (36 ejecuciones por barra). Líneas: Wilson 95%.' (Fuente 24) $gris 40 88
$px0 = 150; $pw = 1250; $py0 = 190; $ph = 640
$lapiz = New-Object System.Drawing.Pen $grisClaro, 2
foreach ($t in 0, 25, 50, 75, 100) {
    $y = $py0 + $ph - $ph * $t / 100
    $g.DrawLine($lapiz, [float]$px0, [float]$y, [float]($px0 + $pw), [float]$y)
    Texto $g "$t%" (Fuente 22) $gris ($px0 - 14) $y 'Far' 'Center'
}
$colores = @{ a = $azul; b = $grisAzul; c = $rojo }
$k = $d.fig2.Count; $slot = $pw / $k
for ($i = 0; $i -lt $k; $i++) {
    $f = $d.fig2[$i]; $bw = 200
    $bx = $px0 + $slot * $i + ($slot - $bw) / 2
    $bh = $ph * $f.p; $by = $py0 + $ph - $bh
    $g.FillRectangle((Pincel $colores[$f.color]), [float]$bx, [float]$by, [float]$bw, [float]$bh)
    $cx = $bx + $bw / 2
    $pw2 = New-Object System.Drawing.Pen (Col '#111827'), 3
    $yl = $py0 + $ph - $ph * $f.lo; $yh = $py0 + $ph - $ph * $f.hi
    $g.DrawLine($pw2, [float]$cx, [float]$yl, [float]$cx, [float]$yh)
    $g.DrawLine($pw2, [float]($cx - 14), [float]$yl, [float]($cx + 14), [float]$yl)
    $g.DrawLine($pw2, [float]($cx - 14), [float]$yh, [float]($cx + 14), [float]$yh)
    Texto $g ("{0}/{1} = {2}" -f $f.k, $f.n, (Pct $f.p)) (Fuente 30 'Bold') $negro $cx ($yh - 14) 'Center' 'Far'
    Texto $g $f.label (Fuente 26) $negro $cx ($py0 + $ph + 18) 'Center'
}
$g.DrawLine((New-Object System.Drawing.Pen (Col '#374151'), 3), [float]$px0, [float]($py0 + $ph), [float]($px0 + $pw), [float]($py0 + $ph))
Guardar $L 'fig2_uno_vs_dos_turnos.png'

# ---------------------------------------------------------------- Figura 3: dilución
$W = 1700; $H = 1130
$L = Lienzo $W $H; $g = $L.g
Texto $g 'El veneno sigue llegando al contexto cuando la base crece' (Fuente 40 'Bold') $negro 40 30
Texto $g 'Solo recuperación (sin llamar al modelo). Media de 3 semillas; documentos sintéticos legítimos agregados al corpus base.' (Fuente 24) $gris 40 88
$px0 = 170; $pw = 1420; $py0 = 190; $ph = 600
$lapiz = New-Object System.Drawing.Pen $grisClaro, 2
foreach ($t in 0, 25, 50, 75, 100) {
    $y = $py0 + $ph - $ph * $t / 100
    $g.DrawLine($lapiz, [float]$px0, [float]$y, [float]($px0 + $pw), [float]$y)
    Texto $g "$t%" (Fuente 22) $gris ($px0 - 14) $y 'Far' 'Center'
}
$m = $d.fig3.x.Count
function PosX($i, $m, $px0, $pw) { $px0 + 90 + ($pw - 180) * $i / ($m - 1) }
for ($i = 0; $i -lt $m; $i++) {
    $x = PosX $i $m $px0 $pw
    Texto $g ('{0:N0}' -f $d.fig3.x[$i]).Replace(',', '.') (Fuente 24) $negro $x ($py0 + $ph + 16) 'Center'
}
Texto $g 'Documentos en la base de conocimiento' (Fuente 24) $gris ($px0 + $pw / 2) ($py0 + $ph + 64) 'Center'
$series = @(
    @{ nombre = 'Veneno propio entre los 4 primeros (k del agente)'; v = $d.fig3.recall4; c = $azul; estilo = 'Solid'; lab = 'abajo' },
    @{ nombre = 'Veneno propio entre los 10 primeros'; v = $d.fig3.recall10; c = $grisAzul; estilo = 'Dash'; lab = 'arriba' },
    @{ nombre = 'Consultas legítimas con veneno entre los 4 primeros'; v = $d.fig3.colateral; c = $naranja; estilo = 'Solid'; lab = 'abajo' }
)
$ly = $py0 + $ph + 120; $lx = 60
foreach ($s in $series) {
    $lap = New-Object System.Drawing.Pen $s.c, 6
    $lap.DashStyle = [System.Drawing.Drawing2D.DashStyle]::$($s.estilo)
    $pts = @()
    for ($i = 0; $i -lt $m; $i++) { $pts += New-Object System.Drawing.PointF ([float](PosX $i $m $px0 $pw)), ([float]($py0 + $ph - $ph * $s.v[$i])) }
    $g.DrawLines($lap, $pts)
    for ($i = 0; $i -lt $m; $i++) {
        $p = $pts[$i]
        $g.FillEllipse((Pincel $s.c), [float]($p.X - 10), [float]($p.Y - 10), 20.0, 20.0)
        $omitir = ($s.nombre -like '*10 primeros*') -and ([Math]::Abs($s.v[$i] - $d.fig3.recall4[$i]) -lt 0.005)
        if (-not $omitir) {
            $dy = if ($s.lab -eq 'arriba') { -16 } else { 16 }
            $va = if ($s.lab -eq 'arriba') { 'Far' } else { 'Near' }
            Texto $g (Pct $s.v[$i]) (Fuente 22 'Bold') (Pincel $s.c) $p.X ($p.Y + $dy) 'Center' $va
        }
    }
    $g.DrawLine($lap, [float]$lx, [float]($ly + 14), [float]($lx + 60), [float]($ly + 14))
    Texto $g $s.nombre (Fuente 22) $negro ($lx + 74) ($ly + 14) 'Near' 'Center'
    $ly += 40
}
Guardar $L 'fig3_dilucion.png'

# ---------------------------------------------------------------- Figura 4: mapa de calor ataque x condición
$filas = $d.fig4.filas; $cols = $d.fig4.cols
$celdaW = 250; $celdaH = 56; $izq = 470; $arr = 190
$W = $izq + $celdaW * $cols.Count + 40; $H = $arr + $celdaH * $filas.Count + 130
$L = Lienzo $W $H; $g = $L.g
Texto $g 'Qué ataques funcionan contra cada modelo (prompt endurecido)' (Fuente 40 'Bold') $negro 40 30
Texto $g 'Cada celda: ejecuciones exitosas / total. Gris = no medido. Mismo corpus (v2), mismo prompt, salvo la última columna (prompt ciego).' (Fuente 22) $gris 40 88
for ($c = 0; $c -lt $cols.Count; $c++) {
    Texto $g $cols[$c] (Fuente 24 'Bold') $negro ($izq + $celdaW * $c + $celdaW / 2) ($arr - 12) 'Center' 'Far'
}
for ($r = 0; $r -lt $filas.Count; $r++) {
    Texto $g $filas[$r] (Fuente 24) $negro ($izq - 14) ($arr + $celdaH * $r + $celdaH / 2) 'Far' 'Center'
    for ($c = 0; $c -lt $cols.Count; $c++) {
        $v = $d.fig4.vals[$r][$c]; $nn = $d.fig4.ns[$r][$c]
        $x = $izq + $celdaW * $c; $y = $arr + $celdaH * $r
        if ($null -eq $v) { $fondo = Col '#E5E7EB'; $txt = 'n/d'; $tc = $gris }
        else {
            $rojoC = Col '#B83227'
            $rr = [int](255 + ($rojoC.R - 255) * $v); $gg = [int](255 + ($rojoC.G - 255) * $v); $bb = [int](255 + ($rojoC.B - 255) * $v)
            $fondo = [System.Drawing.Color]::FromArgb($rr, $gg, $bb)
            $ok = [int][Math]::Round($v * $nn)
            $txt = "$ok/$nn"
            $tc = if ($v -gt 0.55) { Pincel ([System.Drawing.Color]::White) } else { $negro }
        }
        $g.FillRectangle((Pincel $fondo), [float]$x, [float]$y, [float]$celdaW, [float]$celdaH)
        $g.DrawRectangle((New-Object System.Drawing.Pen ([System.Drawing.Color]::White), 3), [float]$x, [float]$y, [float]$celdaW, [float]$celdaH)
        if ($null -eq $v) { $tc = $gris }
        Texto $g $txt (Fuente 24 'Bold') $tc ($x + $celdaW / 2) ($y + $celdaH / 2) 'Center' 'Center'
    }
}
Guardar $L 'fig4_mapa_ataques_modelos.png'

# ---------------------------------------------------------------- Figura 5: sin atacante
$n = $d.fig5.Count
$W = 1700; $filaH = 84; $H = 200 + $n * $filaH + 150
$L = Lienzo $W $H; $g = $L.g
Texto $g 'Acciones indebidas SIN atacante (corpus limpio)' (Fuente 40 'Bold') $negro 40 30
Texto $g ('Claude Haiku 4.5 endurecido; 5 repeticiones por pedido. Utilidad en consultas legítimas: {0}/{1}.' -f $d.utilidad.k, $d.utilidad.n) (Fuente 24) $gris 40 88
$px0 = 830; $pw = 700; $py0 = 170
$lapiz = New-Object System.Drawing.Pen $grisClaro, 2
foreach ($t in 0, 25, 50, 75, 100) {
    $x = $px0 + $pw * $t / 100
    $g.DrawLine($lapiz, [float]$x, [float]$py0, [float]$x, [float]($py0 + $n * $filaH))
    Texto $g "$t%" (Fuente 22) $gris $x ($py0 + $n * $filaH + 8) 'Center'
}
for ($i = 0; $i -lt $n; $i++) {
    $f = $d.fig5[$i]; $y = $py0 + $i * $filaH
    Texto $g $f.label (Fuente 24) $negro ($px0 - 20) ($y + $filaH / 2) 'Far' 'Center'
    $bw = [float]($pw * $f.p); $bh = 44; $by = $y + ($filaH - $bh) / 2
    $c = if ($f.p -gt 0) { $rojo } else { $verde }
    if ($bw -gt 0.5) { $g.FillRectangle((Pincel $c), [float]$px0, [float]$by, $bw, [float]$bh) }
    else { $g.FillRectangle((Pincel $c), [float]$px0, [float]$by, 6.0, [float]$bh) }
    Texto $g ("{0}/{1}" -f $f.k, $f.n) (Fuente 24 'Bold') (Pincel $c) ($px0 + [Math]::Max($bw, 6) + 14) ($by + $bh / 2) 'Near' 'Center'
}
Texto $g 'Rojo: el agente ejecutó una acción sensible que la base confiable reserva a soporte o que el cliente no pidió.' (Fuente 22) $gris 40 ($py0 + $n * $filaH + 64)
Guardar $L 'fig5_sin_atacante.png'
