package com.notas.ui;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Buffer de texto con cursor. Sin selección: lo justo para
 * escribir una nota sin pelearse con el teclado.
 *
 *   flechas / home / end / pgup / pgdn   mover
 *   ctrl+flecha izq/der                  saltar palabra
 *   ctrl+v                               pegar del portapapeles
 *   ctrl+k                               cortar línea (va al portapapeles)
 *   ctrl+z                               deshacer
 *
 * handle() devuelve false cuando no consume la tecla, para que el
 * que llama decida (p.ej. enter en campo de una línea).
 */
public class Editor {

    private static final TextColor CUR_FG = TextColor.ANSI.BLACK;
    private static final TextColor CUR_BG = TextColor.ANSI.GREEN;
    private static final int MAX_UNDO = 200;

    private final boolean multiLinea;
    private final List<StringBuilder> lineas = new ArrayList<>();
    private int fila = 0, col = 0;
    private int scrollFila = 0, scrollCol = 0;
    private int altoVisible = 20; // lo actualiza render(), lo usa pgup/pgdn

    // ── Undo ──────────────────────────────────────────────────────────────────
    // Una racha de tecleo o de borrado se deshace entera; mover el cursor,
    // pegar o cortar abre snapshot nuevo.

    private enum Op { INSERTAR, BORRAR, OTRA }

    private static class Snap {
        final String texto;
        final int fila, col;
        Snap(String texto, int fila, int col) { this.texto = texto; this.fila = fila; this.col = col; }
    }

    private final Deque<Snap> pilaUndo = new ArrayDeque<>();
    private Op ultimaOp = Op.OTRA;

    private void recordar(Op op) {
        if (op != ultimaOp || op == Op.OTRA) {
            pilaUndo.push(new Snap(getTexto(), fila, col));
            if (pilaUndo.size() > MAX_UNDO) pilaUndo.removeLast();
        }
        ultimaOp = op;
    }

    private void deshacerUltimo() {
        if (pilaUndo.isEmpty()) return;
        Snap s = pilaUndo.pop();
        cargar(s.texto);
        fila = Math.min(s.fila, lineas.size() - 1);
        col = Math.min(s.col, lineas.get(fila).length());
        ultimaOp = Op.OTRA;
    }

    public Editor(boolean multiLinea) {
        this.multiLinea = multiLinea;
        lineas.add(new StringBuilder());
    }

    // ── Texto ─────────────────────────────────────────────────────────────────

    public void setTexto(String texto) {
        cargar(texto);
        fila = lineas.size() - 1;
        col = lineas.get(fila).length();
        scrollFila = 0;
        scrollCol = 0;
        pilaUndo.clear();
        ultimaOp = Op.OTRA;
    }

    private void cargar(String texto) {
        lineas.clear();
        for (String l : normalizar(texto).split("\n", -1)) lineas.add(new StringBuilder(l));
        if (lineas.isEmpty()) lineas.add(new StringBuilder());
    }

    public String getTexto() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lineas.size(); i++) {
            if (i > 0) sb.append('\n');
            sb.append(lineas.get(i));
        }
        return sb.toString();
    }

    public int getFila() { return fila; }
    public int getCol()  { return col; }

    private String normalizar(String s) {
        s = s.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ");
        return multiLinea ? s : s.replace('\n', ' ');
    }

    // ── Teclado ───────────────────────────────────────────────────────────────

    public boolean handle(KeyStroke key) {
        StringBuilder linea = lineas.get(fila);
        switch (key.getKeyType()) {
            case Character: {
                char c = key.getCharacter();
                if (key.isCtrlDown()) {
                    if (c == 'v') { recordar(Op.OTRA); pegar(); return true; }
                    if (c == 'k') { recordar(Op.OTRA); cortarLinea(); return true; }
                    if (c == 'z') { deshacerUltimo(); return true; }
                    return false;
                }
                recordar(Op.INSERTAR);
                linea.insert(col++, c);
                return true;
            }
            case Tab:
                recordar(Op.INSERTAR);
                linea.insert(col, "    ");
                col += 4;
                return true;
            case Enter:
                if (!multiLinea) return false;
                recordar(Op.OTRA);
                String resto = linea.substring(col);
                linea.setLength(col);
                lineas.add(++fila, new StringBuilder(resto));
                col = 0;
                return true;
            case Backspace:
                if (col > 0) {
                    recordar(Op.BORRAR);
                    linea.deleteCharAt(--col);
                } else if (fila > 0) {
                    recordar(Op.BORRAR);
                    StringBuilder previa = lineas.get(fila - 1);
                    col = previa.length();
                    previa.append(linea);
                    lineas.remove(fila--);
                }
                return true;
            case Delete:
                if (col < linea.length()) {
                    recordar(Op.BORRAR);
                    linea.deleteCharAt(col);
                } else if (fila < lineas.size() - 1) {
                    recordar(Op.BORRAR);
                    linea.append(lineas.remove(fila + 1));
                }
                return true;
            case ArrowLeft:
                ultimaOp = Op.OTRA;
                if (key.isCtrlDown()) { saltarPalabraIzq(); return true; }
                if (col > 0) col--;
                else if (fila > 0) { fila--; col = lineas.get(fila).length(); }
                return true;
            case ArrowRight:
                ultimaOp = Op.OTRA;
                if (key.isCtrlDown()) { saltarPalabraDer(); return true; }
                if (col < linea.length()) col++;
                else if (fila < lineas.size() - 1) { fila++; col = 0; }
                return true;
            case ArrowUp:
                ultimaOp = Op.OTRA;
                if (fila == 0) return false; // el que llama decide (p.ej. saltar al título)
                fila--;
                clampCol();
                return true;
            case ArrowDown:
                ultimaOp = Op.OTRA;
                if (fila == lineas.size() - 1) { col = linea.length(); return true; }
                fila++;
                clampCol();
                return true;
            case Home:
                ultimaOp = Op.OTRA;
                col = 0;
                return true;
            case End:
                ultimaOp = Op.OTRA;
                col = linea.length();
                return true;
            case PageUp:
                ultimaOp = Op.OTRA;
                fila = Math.max(0, fila - altoVisible);
                clampCol();
                return true;
            case PageDown:
                ultimaOp = Op.OTRA;
                fila = Math.min(lineas.size() - 1, fila + altoVisible);
                clampCol();
                return true;
            default:
                return false;
        }
    }

    private void clampCol() {
        col = Math.min(col, lineas.get(fila).length());
    }

    private void saltarPalabraIzq() {
        if (col == 0 && fila > 0) { fila--; col = lineas.get(fila).length(); return; }
        String l = lineas.get(fila).toString();
        while (col > 0 && Character.isWhitespace(l.charAt(col - 1))) col--;
        while (col > 0 && !Character.isWhitespace(l.charAt(col - 1))) col--;
    }

    private void saltarPalabraDer() {
        String l = lineas.get(fila).toString();
        if (col == l.length() && fila < lineas.size() - 1) { fila++; col = 0; return; }
        while (col < l.length() && !Character.isWhitespace(l.charAt(col))) col++;
        while (col < l.length() && Character.isWhitespace(l.charAt(col))) col++;
    }

    // ── Portapapeles ──────────────────────────────────────────────────────────

    private void pegar() {
        String texto = normalizar(dePortapapeles());
        if (texto.isEmpty()) return;
        String[] partes = texto.split("\n", -1);
        StringBuilder linea = lineas.get(fila);
        if (partes.length == 1) {
            linea.insert(col, partes[0]);
            col += partes[0].length();
            return;
        }
        String resto = linea.substring(col);
        linea.setLength(col);
        linea.append(partes[0]);
        for (int i = 1; i < partes.length; i++) lineas.add(++fila, new StringBuilder(partes[i]));
        col = lineas.get(fila).length();
        lineas.get(fila).append(resto);
    }

    private void cortarLinea() {
        alPortapapeles(lineas.get(fila).toString() + (multiLinea ? "\n" : ""));
        if (lineas.size() == 1) {
            lineas.get(0).setLength(0);
        } else {
            lineas.remove(fila);
            if (fila >= lineas.size()) fila = lineas.size() - 1;
        }
        col = 0;
        clampCol();
    }

    public static void alPortapapeles(String texto) {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(texto), null);
        } catch (Exception ignorada) {}
    }

    private static String dePortapapeles() {
        try {
            Object datos = Toolkit.getDefaultToolkit().getSystemClipboard()
                    .getData(DataFlavor.stringFlavor);
            return datos == null ? "" : datos.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ── Render ────────────────────────────────────────────────────────────────

    public void render(TextGraphics g, int x, int y, int w, int h, boolean conFoco) {
        altoVisible = h;
        ajustarScroll(w, h);

        for (int i = 0; i < h; i++) {
            int idx = scrollFila + i;
            if (idx >= lineas.size()) break;
            String l = lineas.get(idx).toString();
            if (l.length() > scrollCol) {
                g.putString(x, y + i, l.substring(scrollCol, Math.min(l.length(), scrollCol + w)));
            }
        }

        if (conFoco) {
            String l = lineas.get(fila).toString();
            char bajoCursor = col < l.length() ? l.charAt(col) : ' ';
            TextColor fg = g.getForegroundColor(), bg = g.getBackgroundColor();
            g.setForegroundColor(CUR_FG);
            g.setBackgroundColor(CUR_BG);
            g.setCharacter(x + col - scrollCol, y + fila - scrollFila, bajoCursor);
            g.setForegroundColor(fg);
            g.setBackgroundColor(bg);
        }
    }

    private void ajustarScroll(int w, int h) {
        if (fila < scrollFila) scrollFila = fila;
        if (fila >= scrollFila + h) scrollFila = fila - h + 1;
        if (col < scrollCol) scrollCol = col;
        if (col >= scrollCol + w) scrollCol = col - w + 1;
    }
}
