Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

Add-Type @"
using System;
using System.Runtime.InteropServices;
public class WinAPI {
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr hWnd, int nCmdShow);
    [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr hWnd, out RECT rect);
    [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr hWnd, IntPtr hdcBlt, int nFlags);
    [DllImport("user32.dll")] public static extern IntPtr GetDC(IntPtr hWnd);
    [DllImport("user32.dll")] public static extern int ReleaseDC(IntPtr hWnd, IntPtr hDC);
    [DllImport("gdi32.dll")] public static extern bool BitBlt(IntPtr hdc, int nXDest, int nYDest, int nWidth, int nHeight, IntPtr hdcSrc, int nXSrc, int nYSrc, int dwRop);
    [DllImport("gdi32.dll")] public static extern IntPtr CreateCompatibleDC(IntPtr hdc);
    [DllImport("gdi32.dll")] public static extern IntPtr CreateCompatibleBitmap(IntPtr hdc, int nWidth, int nHeight);
    [DllImport("gdi32.dll")] public static extern IntPtr SelectObject(IntPtr hdc, IntPtr hgdiobj);
    [DllImport("gdi32.dll")] public static extern bool DeleteDC(IntPtr hdc);
    [DllImport("gdi32.dll")] public static extern bool DeleteObject(IntPtr hgdiobj);
    [StructLayout(LayoutKind.Sequential)]
    public struct RECT { public int Left, Top, Right, Bottom; }
}
"@

$imgDir = "C:\Users\Santi\Desktop\Work-Port\portfolio-systems\public\images\notas"

function Capture-Window {
    param([IntPtr]$hwnd, [string]$filename)

    $rect = New-Object WinAPI+RECT
    [WinAPI]::GetWindowRect($hwnd, [ref]$rect) | Out-Null
    $w = $rect.Right - $rect.Left
    $h = $rect.Bottom - $rect.Top

    $bmp = New-Object System.Drawing.Bitmap($w, $h)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.CopyFromScreen($rect.Left, $rect.Top, 0, 0, [System.Drawing.Size]::new($w, $h))
    $g.Dispose()

    $bmp.Save("$imgDir\$filename")
    $bmp.Dispose()
    Write-Host "Captured: $filename"
}

function Send-And-Wait {
    param([string]$keys, [int]$ms = 600)
    [System.Windows.Forms.SendKeys]::SendWait($keys)
    Start-Sleep -Milliseconds $ms
}

# Find the notas window
$proc = Get-Process | Where-Object { $_.MainWindowTitle -eq "notas" } | Select-Object -First 1
if (-not $proc) { Write-Error "notas no encontrado"; exit 1 }
$hwnd = $proc.MainWindowHandle
Write-Host "Found notas window: $hwnd"

# Bring to front
[WinAPI]::ShowWindow($hwnd, 9) | Out-Null   # SW_RESTORE
[WinAPI]::SetForegroundWindow($hwnd) | Out-Null
Start-Sleep -Milliseconds 800

# ── STATE 1: List view (root) ──────────────────────────────────────────────
Capture-Window $hwnd "01_lista_raiz.png"

# Navigate to first note (should be a favorite)
Send-And-Wait "j" 300
Capture-Window $hwnd "02_lista_seleccion.png"

# ── STATE 2: VIEW a note ───────────────────────────────────────────────────
Send-And-Wait "{ENTER}" 400
Capture-Window $hwnd "03_vista_nota.png"

# Scroll down in view
Send-And-Wait "j" 200
Send-And-Wait "j" 200
Capture-Window $hwnd "04_vista_nota_scroll.png"

# ── STATE 3: EDITOR ────────────────────────────────────────────────────────
Send-And-Wait "e" 400
Capture-Window $hwnd "05_editor_cuerpo.png"

# Move focus to title
Send-And-Wait "k" 300
Send-And-Wait "k" 300
Capture-Window $hwnd "06_editor_titulo.png"

# Back to list
Send-And-Wait "{ESC}" 400

# ── STATE 4: New note ──────────────────────────────────────────────────────
Send-And-Wait "n" 400
Capture-Window $hwnd "07_nueva_nota.png"

# Type a title
Send-And-Wait "P" 100
Send-And-Wait "r" 100
Send-And-Wait "u" 100
Send-And-Wait "e" 100
Send-And-Wait "b" 100
Send-And-Wait "a" 100
Send-And-Wait " " 100
Send-And-Wait "C" 100
Send-And-Wait "a" 100
Send-And-Wait "p" 100
Send-And-Wait "t" 100
Send-And-Wait "u" 100
Send-And-Wait "r" 100
Send-And-Wait "a" 100
Capture-Window $hwnd "08_nueva_nota_titulo.png"

# Move to content
Send-And-Wait "{ENTER}" 300
Send-And-Wait "{ENTER}" 300

# Type content
Send-And-Wait "N" 80; Send-And-Wait "o" 80; Send-And-Wait "t" 80
Send-And-Wait "a" 80; Send-And-Wait " " 80; Send-And-Wait "d" 80
Send-And-Wait "e" 80; Send-And-Wait " " 80; Send-And-Wait "p" 80
Send-And-Wait "r" 80; Send-And-Wait "u" 80; Send-And-Wait "e" 80
Send-And-Wait "b" 80; Send-And-Wait "a" 80
Send-And-Wait "{ENTER}" 200
Send-And-Wait "C" 80; Send-And-Wait "r" 80; Send-And-Wait "e" 80
Send-And-Wait "a" 80; Send-And-Wait "d" 80; Send-And-Wait "a" 80
Send-And-Wait " " 80; Send-And-Wait "p" 80; Send-And-Wait "o" 80
Send-And-Wait "r" 80; Send-And-Wait " " 80; Send-And-Wait "C" 80
Send-And-Wait "l" 80; Send-And-Wait "a" 80; Send-And-Wait "u" 80
Send-And-Wait "d" 80; Send-And-Wait "e" 80
Capture-Window $hwnd "09_editor_escribiendo.png"

# Save with Ctrl+S
Send-And-Wait "^s" 600
Capture-Window $hwnd "10_lista_tras_guardar.png"

# ── STATE 5: Enter a folder ────────────────────────────────────────────────
Send-And-Wait "g" 300
Capture-Window $hwnd "11_lista_con_carpetas.png"

# Select 'personal' folder (it's first alphabetically)
Send-And-Wait "{ENTER}" 400
Capture-Window $hwnd "12_carpeta_personal.png"

# View a note inside folder
Send-And-Wait "j" 300
Send-And-Wait "{ENTER}" 400
Capture-Window $hwnd "13_nota_en_carpeta.png"
Send-And-Wait "{ESC}" 300

# Back to root
Send-And-Wait "h" 400
Capture-Window $hwnd "14_volver_raiz.png"

# ── STATE 6: SEARCH ────────────────────────────────────────────────────────
Send-And-Wait "/" 300
Capture-Window $hwnd "15_busqueda_abierta.png"

Send-And-Wait "g" 80; Send-And-Wait "i" 80; Send-And-Wait "t" 80
Capture-Window $hwnd "16_busqueda_resultados.png"

Send-And-Wait "{ESC}" 400

# ── STATE 7: FAVORITE toggle ───────────────────────────────────────────────
# Go to a note and toggle fav
Send-And-Wait "G" 300
Capture-Window $hwnd "17_lista_bottom.png"
Send-And-Wait "f" 400
Capture-Window $hwnd "18_favorito_toggle.png"

# ── STATE 8: MOVE note ─────────────────────────────────────────────────────
Send-And-Wait "g" 300
Send-And-Wait "j" 300
Send-And-Wait "j" 300
Send-And-Wait "j" 300
Send-And-Wait "m" 400
Capture-Window $hwnd "19_mover_nota.png"
Send-And-Wait "{ESC}" 300

# ── STATE 9: RENAME ────────────────────────────────────────────────────────
Send-And-Wait "g" 300
Send-And-Wait "r" 400
Capture-Window $hwnd "20_renombrar.png"
Send-And-Wait "{ESC}" 300

# ── STATE 10: Delete a note (to trash) ────────────────────────────────────
# Select the test note we created
Send-And-Wait "G" 300
Capture-Window $hwnd "21_antes_borrar.png"
Send-And-Wait "d" 600
Capture-Window $hwnd "22_tras_borrar_aviso.png"

# ── STATE 11: TRASH view ───────────────────────────────────────────────────
Send-And-Wait "t" 400
Capture-Window $hwnd "23_papelera.png"

# Restore the note
Send-And-Wait "r" 600
Capture-Window $hwnd "24_restaurada.png"

# Try to delete permanently
Send-And-Wait "j" 300
# Actually let's keep notes - just capture the confirm purge dialog
# First move something else to trash to have content
Send-And-Wait "{ESC}" 400
Send-And-Wait "G" 300
Send-And-Wait "d" 400
Send-And-Wait "t" 400
Capture-Window $hwnd "25_papelera_con_nota.png"
Send-And-Wait "d" 400
Capture-Window $hwnd "26_confirmar_eliminar.png"
Send-And-Wait "n" 400   # cancel (not y/s)
Capture-Window $hwnd "27_papelera_cancelado.png"
Send-And-Wait "{ESC}" 400

# ── STATE 12: HELP ─────────────────────────────────────────────────────────
Send-And-Wait "{ESC}" 300
Send-And-Wait "?" 400
Capture-Window $hwnd "28_ayuda.png"
Send-And-Wait " " 400

# ── Final: clean list ──────────────────────────────────────────────────────
Send-And-Wait "g" 300
Capture-Window $hwnd "29_lista_final.png"

# Enter trabajo folder
Send-And-Wait "j" 300
Send-And-Wait "{ENTER}" 400
Capture-Window $hwnd "30_carpeta_trabajo.png"

Write-Host "`nDone! All screenshots saved to $imgDir"
