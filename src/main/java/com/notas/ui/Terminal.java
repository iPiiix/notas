package com.notas.ui;

import com.googlecode.lanterna.Symbols;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.notas.datos.Archivos;
import com.notas.model.Nota;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Terminal {

    private enum State { LIST, VIEW, NEW_TITLE, NEW_CONTENT, EDIT_CONTENT, CONFIRM_DELETE, SEARCH }

    private Screen screen;
    private final Archivos archivos = new Archivos();
    private List<Nota> notas = new ArrayList<>();
    private List<Nota> filtradas = new ArrayList<>();
    private int selIdx = 0;
    private int scroll = 0;
    private State estado = State.LIST;

    private String inputTitulo = "";
    private String inputContenido = "";
    private String inputBusqueda = "";
    private Nota editando = null;

    public void iniciar() throws Exception {
        com.googlecode.lanterna.terminal.Terminal term = new DefaultTerminalFactory().createTerminal();
        screen = new TerminalScreen(term);
        screen.startScreen();
        screen.setCursorPosition(null);

        recargar();

        while (true) {
            render();
            KeyStroke key = screen.readInput();
            if (!handle(key)) break;
        }

        screen.stopScreen();
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    private void recargar() {
        notas = archivos.loadAll();
        notas.sort((a, b) -> b.getFechaActualizacion().compareTo(a.getFechaActualizacion()));
        aplicarFiltro();
    }

    private void aplicarFiltro() {
        if (inputBusqueda.isEmpty()) {
            filtradas = new ArrayList<>(notas);
        } else {
            String q = inputBusqueda.toLowerCase();
            filtradas = notas.stream()
                    .filter(n -> n.getTitulo().toLowerCase().contains(q)
                            || n.getContenido().toLowerCase().contains(q))
                    .collect(Collectors.toList());
        }
        if (selIdx >= filtradas.size()) selIdx = Math.max(0, filtradas.size() - 1);
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    private boolean handle(KeyStroke key) {
        switch (estado) {
            case LIST:           return handleList(key);
            case VIEW:           return handleView(key);
            case NEW_TITLE:      return handleNewTitle(key);
            case NEW_CONTENT:    return handleNewContent(key);
            case EDIT_CONTENT:   return handleEditContent(key);
            case CONFIRM_DELETE: return handleConfirmDelete(key);
            case SEARCH:         return handleSearch(key);
            default:             return true;
        }
    }

    private boolean handleList(KeyStroke key) {
        if (key.getKeyType() == KeyType.Character) {
            char c = key.getCharacter();
            switch (c) {
                case 'q': return false;
                case 'j': navAbajo(); break;
                case 'k': navArriba(); break;
                case 'n': inputTitulo = ""; inputContenido = ""; editando = null; estado = State.NEW_TITLE; break;
                case 'd': if (!filtradas.isEmpty()) estado = State.CONFIRM_DELETE; break;
                case 'f': toggleFav(); break;
                case '/': inputBusqueda = ""; estado = State.SEARCH; break;
            }
        } else if (key.getKeyType() == KeyType.Enter && !filtradas.isEmpty()) {
            estado = State.VIEW;
        } else if (key.getKeyType() == KeyType.ArrowDown) {
            navAbajo();
        } else if (key.getKeyType() == KeyType.ArrowUp) {
            navArriba();
        }
        return true;
    }

    private boolean handleView(KeyStroke key) {
        if (key.getKeyType() == KeyType.Escape) { estado = State.LIST; return true; }
        if (key.getKeyType() == KeyType.Character) {
            char c = key.getCharacter();
            if (c == 'q') { estado = State.LIST; return true; }
            if (c == 'e' && !filtradas.isEmpty()) {
                editando = filtradas.get(selIdx);
                inputTitulo = editando.getTitulo();
                inputContenido = editando.getContenido();
                estado = State.EDIT_CONTENT;
            }
        }
        return true;
    }

    private boolean handleNewTitle(KeyStroke key) {
        if (key.getKeyType() == KeyType.Escape) { estado = State.LIST; return true; }
        if (key.getKeyType() == KeyType.Enter) { estado = State.NEW_CONTENT; return true; }
        inputTitulo = applyEdit(inputTitulo, key);
        return true;
    }

    private boolean handleNewContent(KeyStroke key) {
        if (key.getKeyType() == KeyType.Escape) { estado = State.LIST; return true; }
        if (isCtrlS(key)) {
            if (!inputTitulo.isBlank()) {
                archivos.save(new Nota(inputTitulo.strip(), inputContenido));
                recargar();
            }
            estado = State.LIST;
            return true;
        }
        if (key.getKeyType() == KeyType.Enter) { inputContenido += "\n"; return true; }
        inputContenido = applyEdit(inputContenido, key);
        return true;
    }

    private boolean handleEditContent(KeyStroke key) {
        if (key.getKeyType() == KeyType.Escape) { estado = State.VIEW; return true; }
        if (isCtrlS(key)) {
            editando.setTitulo(inputTitulo.strip());
            editando.setContenido(inputContenido);
            editando.setFechaActualizacion(LocalDate.now());
            archivos.save(editando);
            recargar();
            estado = State.VIEW;
            return true;
        }
        if (key.getKeyType() == KeyType.Enter) { inputContenido += "\n"; return true; }
        inputContenido = applyEdit(inputContenido, key);
        return true;
    }

    private boolean handleConfirmDelete(KeyStroke key) {
        if (key.getKeyType() == KeyType.Character) {
            char c = key.getCharacter();
            if (c == 'y' || c == 's') {
                archivos.delete(filtradas.get(selIdx).getId());
                recargar();
                selIdx = Math.min(selIdx, Math.max(0, filtradas.size() - 1));
            }
        }
        estado = State.LIST;
        return true;
    }

    private boolean handleSearch(KeyStroke key) {
        if (key.getKeyType() == KeyType.Escape) {
            inputBusqueda = "";
            aplicarFiltro();
            estado = State.LIST;
            return true;
        }
        if (key.getKeyType() == KeyType.Enter) { estado = State.LIST; return true; }
        if (key.getKeyType() == KeyType.Backspace && !inputBusqueda.isEmpty()) {
            inputBusqueda = inputBusqueda.substring(0, inputBusqueda.length() - 1);
        } else if (key.getKeyType() == KeyType.Character) {
            inputBusqueda += key.getCharacter();
        }
        aplicarFiltro();
        return true;
    }

    private String applyEdit(String text, KeyStroke key) {
        if (key.getKeyType() == KeyType.Backspace && !text.isEmpty())
            return text.substring(0, text.length() - 1);
        if (key.getKeyType() == KeyType.Character && !key.isCtrlDown())
            return text + key.getCharacter();
        return text;
    }

    private boolean isCtrlS(KeyStroke key) {
        return key.getKeyType() == KeyType.Character && key.isCtrlDown() && key.getCharacter() == 's';
    }

    private void navArriba() {
        if (selIdx > 0) {
            selIdx--;
            if (selIdx < scroll) scroll = selIdx;
        }
    }

    private void navAbajo() {
        if (selIdx < filtradas.size() - 1) {
            selIdx++;
            int h = contentRows();
            if (selIdx >= scroll + h) scroll = selIdx - h + 1;
        }
    }

    private void toggleFav() {
        if (filtradas.isEmpty()) return;
        Nota n = filtradas.get(selIdx);
        n.setFavorito(!n.isFavorito());
        archivos.save(n);
        recargar();
    }

    private int contentRows() {
        return screen.getTerminalSize().getRows() - 4;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────
    //
    // Layout (rows = terminal height):
    //   row 0          top border  ┌─ notas ──────┬──────────────────┐
    //   rows 1..rows-4 content     │ list         │ note             │
    //   row rows-3     separator   ├──────────────┴──────────────────┤
    //   row rows-2     status      │ keybindings                     │
    //   row rows-1     bottom      └─────────────────────────────────┘

    private void render() throws Exception {
        screen.clear();
        int cols = screen.getTerminalSize().getColumns();
        int rows = screen.getTerminalSize().getRows();
        TextGraphics g = screen.newTextGraphics();
        g.setForegroundColor(TextColor.ANSI.WHITE);
        g.setBackgroundColor(TextColor.ANSI.BLACK);

        boolean inEditor = estado == State.NEW_TITLE
                || estado == State.NEW_CONTENT
                || estado == State.EDIT_CONTENT;

        if (inEditor) {
            renderEditor(g, cols, rows);
        } else {
            int listW = 30;
            renderFrame(g, cols, rows, listW);
            renderList(g, cols, rows, listW);
            renderNote(g, cols, rows, listW);
            renderStatus(g, cols, rows);
        }

        screen.refresh();
    }

    private void renderFrame(TextGraphics g, int cols, int rows, int listW) {
        // Top border
        g.setCharacter(0, 0, Symbols.SINGLE_LINE_TOP_LEFT_CORNER);
        g.setCharacter(cols - 1, 0, Symbols.SINGLE_LINE_TOP_RIGHT_CORNER);
        hline(g, 1, cols - 2, 0);
        g.putString(4, 0, " notas ");
        g.setCharacter(listW + 1, 0, Symbols.SINGLE_LINE_T_DOWN);

        // Side borders
        vline(g, 0, 1, rows - 2);
        vline(g, cols - 1, 1, rows - 2);

        // Vertical divider (content rows only)
        vline(g, listW + 1, 1, rows - 4);

        // Separator
        g.setCharacter(0, rows - 3, Symbols.SINGLE_LINE_T_RIGHT);
        g.setCharacter(cols - 1, rows - 3, Symbols.SINGLE_LINE_T_LEFT);
        g.setCharacter(listW + 1, rows - 3, Symbols.SINGLE_LINE_T_UP);
        hline(g, 1, cols - 2, rows - 3);

        // Bottom border
        g.setCharacter(0, rows - 1, Symbols.SINGLE_LINE_BOTTOM_LEFT_CORNER);
        g.setCharacter(cols - 1, rows - 1, Symbols.SINGLE_LINE_BOTTOM_RIGHT_CORNER);
        hline(g, 1, cols - 2, rows - 1);
    }

    private void renderList(TextGraphics g, int cols, int rows, int listW) {
        int height = contentRows();

        if (estado == State.SEARCH) {
            g.setForegroundColor(TextColor.ANSI.YELLOW);
            String bar = "/ " + inputBusqueda + "█";
            if (bar.length() > listW - 1) bar = bar.substring(0, listW - 1);
            g.putString(1, 1, bar);
            g.setForegroundColor(TextColor.ANSI.WHITE);
        }

        int startRow = (estado == State.SEARCH) ? 2 : 1;
        int visible  = (estado == State.SEARCH) ? height - 1 : height;

        for (int i = 0; i < visible; i++) {
            int idx = scroll + i;
            if (idx >= filtradas.size()) break;
            int row = startRow + i;
            Nota n = filtradas.get(idx);
            boolean sel = (idx == selIdx);

            String fav   = n.isFavorito() ? "★" : " ";
            String title = n.getTitulo();
            int maxLen   = listW - 4;
            if (title.length() > maxLen) title = title.substring(0, maxLen - 1) + "…";

            StringBuilder line = new StringBuilder(" ").append(fav).append(" ").append(title);
            while (line.length() < listW) line.append(' ');
            String row_str = line.substring(0, listW);

            if (sel) {
                g.setForegroundColor(TextColor.ANSI.BLACK);
                g.setBackgroundColor(TextColor.ANSI.WHITE);
            }
            g.putString(1, row, row_str);
            if (sel) {
                g.setForegroundColor(TextColor.ANSI.WHITE);
                g.setBackgroundColor(TextColor.ANSI.BLACK);
            }
        }

        if (!filtradas.isEmpty()) {
            g.setForegroundColor(TextColor.ANSI.CYAN);
            g.putString(1, rows - 4, " " + (selIdx + 1) + "/" + filtradas.size());
            g.setForegroundColor(TextColor.ANSI.WHITE);
        }
    }

    private void renderNote(TextGraphics g, int cols, int rows, int listW) {
        int startCol  = listW + 2;
        int contentW  = cols - startCol - 1;
        int maxLines  = contentRows() - 3;

        if (filtradas.isEmpty()) {
            g.setForegroundColor(TextColor.ANSI.CYAN);
            String msg = "sin notas  —  n para crear";
            int mx = startCol + (contentW - msg.length()) / 2;
            g.putString(Math.max(startCol, mx), rows / 2, msg);
            g.setForegroundColor(TextColor.ANSI.WHITE);
            return;
        }

        Nota n = filtradas.get(selIdx);

        // Title
        String title = n.getTitulo();
        if (title.length() > contentW) title = title.substring(0, contentW - 1) + "…";
        g.putString(startCol, 1, title);

        // Underline
        g.setForegroundColor(TextColor.ANSI.CYAN);
        int sepLen = Math.min(title.length() + 2, contentW);
        StringBuilder sep = new StringBuilder();
        for (int i = 0; i < sepLen; i++) sep.append(Symbols.SINGLE_LINE_HORIZONTAL);
        g.putString(startCol, 2, sep.toString());
        g.setForegroundColor(TextColor.ANSI.WHITE);

        // Content
        String[] lines = n.getContenido().split("\n", -1);
        for (int i = 0; i < Math.min(lines.length, maxLines); i++) {
            String line = lines[i];
            if (line.length() > contentW) line = line.substring(0, contentW - 1) + "…";
            g.putString(startCol, 3 + i, line);
        }

        // Meta
        g.setForegroundColor(TextColor.ANSI.CYAN);
        String meta = n.getFechaCreacion().toString();
        if (!n.getTags().isEmpty()) meta += "  " + String.join(" #", n.getTags());
        if (meta.length() > contentW) meta = meta.substring(0, contentW - 1);
        g.putString(startCol, rows - 4, meta);
        g.setForegroundColor(TextColor.ANSI.WHITE);
    }

    private void renderStatus(TextGraphics g, int cols, int rows) {
        g.setForegroundColor(TextColor.ANSI.CYAN);
        String s;
        switch (estado) {
            case VIEW:           s = "  esc/q volver  e editar"; break;
            case CONFIRM_DELETE: s = "  borrar?  s/y confirmar  cualquier otra tecla cancela"; break;
            case SEARCH:         s = "  esc cancelar  enter confirmar"; break;
            default:             s = "  j/k nav  enter ver  n nueva  d borrar  f fav  / buscar  q salir"; break;
        }
        if (s.length() > cols - 2) s = s.substring(0, cols - 2);
        g.putString(1, rows - 2, s);
        g.setForegroundColor(TextColor.ANSI.WHITE);
    }

    private void renderEditor(TextGraphics g, int cols, int rows) {
        boolean isEdit = (estado == State.EDIT_CONTENT);

        // Frame
        g.setCharacter(0, 0, Symbols.SINGLE_LINE_TOP_LEFT_CORNER);
        g.setCharacter(cols - 1, 0, Symbols.SINGLE_LINE_TOP_RIGHT_CORNER);
        hline(g, 1, cols - 2, 0);
        g.putString(4, 0, isEdit ? " editar nota " : " nueva nota ");
        vline(g, 0, 1, rows - 2);
        vline(g, cols - 1, 1, rows - 2);
        g.setCharacter(0, rows - 3, Symbols.SINGLE_LINE_T_RIGHT);
        g.setCharacter(cols - 1, rows - 3, Symbols.SINGLE_LINE_T_LEFT);
        hline(g, 1, cols - 2, rows - 3);
        g.setCharacter(0, rows - 1, Symbols.SINGLE_LINE_BOTTOM_LEFT_CORNER);
        g.setCharacter(cols - 1, rows - 1, Symbols.SINGLE_LINE_BOTTOM_RIGHT_CORNER);
        hline(g, 1, cols - 2, rows - 1);

        // Title row
        g.setForegroundColor(TextColor.ANSI.CYAN);
        g.putString(2, 1, "titulo:");
        g.setForegroundColor(TextColor.ANSI.WHITE);

        boolean editingTitle = (estado == State.NEW_TITLE);
        String tDisp = inputTitulo + (editingTitle ? "█" : "");
        if (editingTitle) g.setForegroundColor(TextColor.ANSI.YELLOW);
        if (tDisp.length() > cols - 12) tDisp = tDisp.substring(0, cols - 12);
        g.putString(10, 1, tDisp);
        g.setForegroundColor(TextColor.ANSI.WHITE);

        // Divider
        g.setForegroundColor(TextColor.ANSI.CYAN);
        hline(g, 2, cols - 3, 2);
        g.setForegroundColor(TextColor.ANSI.WHITE);

        // Content
        boolean editingContent = (estado == State.NEW_CONTENT || estado == State.EDIT_CONTENT);
        String[] lines = inputContenido.split("\n", -1);
        int maxLines = rows - 7;

        if (editingContent && inputContenido.isEmpty()) {
            g.setForegroundColor(TextColor.ANSI.YELLOW);
            g.putString(2, 3, "█");
            g.setForegroundColor(TextColor.ANSI.WHITE);
        }

        for (int i = 0; i < Math.min(lines.length, maxLines); i++) {
            String line = lines[i];
            boolean isLast = (i == lines.length - 1);
            if (isLast && editingContent) {
                g.setForegroundColor(TextColor.ANSI.YELLOW);
                line = line + "█";
            }
            if (line.length() > cols - 4) line = line.substring(0, cols - 5) + "…";
            g.putString(2, 3 + i, line);
            g.setForegroundColor(TextColor.ANSI.WHITE);
        }

        // Status
        g.setForegroundColor(TextColor.ANSI.CYAN);
        String hint = editingTitle ? "  enter continuar  esc cancelar" : "  ctrl+s guardar  esc cancelar";
        g.putString(1, rows - 2, hint);
        g.setForegroundColor(TextColor.ANSI.WHITE);
    }

    // ── Primitives ────────────────────────────────────────────────────────────

    private void hline(TextGraphics g, int startCol, int endCol, int row) {
        for (int c = startCol; c <= endCol; c++) g.setCharacter(c, row, Symbols.SINGLE_LINE_HORIZONTAL);
    }

    private void vline(TextGraphics g, int col, int startRow, int endRow) {
        for (int r = startRow; r <= endRow; r++) g.setCharacter(col, r, Symbols.SINGLE_LINE_VERTICAL);
    }
}
