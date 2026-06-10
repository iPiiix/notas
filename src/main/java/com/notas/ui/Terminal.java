package com.notas.ui;

import com.googlecode.lanterna.Symbols;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.swing.SwingTerminalFrame;
import com.googlecode.lanterna.terminal.swing.TerminalEmulatorAutoCloseTrigger;
import com.notas.datos.Archivos;
import com.notas.model.Nota;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Terminal {

    // ── Palette ───────────────────────────────────────────────────────────────
    private static final TextColor FG     = TextColor.ANSI.GREEN;
    private static final TextColor BG     = TextColor.ANSI.BLACK;
    private static final TextColor FG_DIM = TextColor.ANSI.CYAN;
    private static final TextColor FG_HI  = TextColor.ANSI.WHITE;
    private static final TextColor FG_FAV = TextColor.ANSI.YELLOW;
    private static final TextColor SEL_FG = TextColor.ANSI.BLACK;
    private static final TextColor SEL_BG = TextColor.ANSI.GREEN;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("MM-dd");

    private enum State { LIST, VIEW, EDITOR, CONFIRM_PURGE, SEARCH, TRASH, MOVE, RENAME, HELP }
    private enum Foco { TITULO, TAGS, CUERPO }

    private Screen screen;
    private final Archivos archivos = new Archivos();
    private List<Nota> notas = new ArrayList<>();
    private List<Nota> filtradas = new ArrayList<>();
    private List<String> carpetas = new ArrayList<>();
    private String carpetaActual = "";
    private int selIdx = 0;
    private int scroll = 0;
    private State estado = State.LIST;

    private final Editor tituloEd = new Editor(false);
    private final Editor tagsEd = new Editor(false);
    private final Editor promptEd = new Editor(false); // mover / renombrar
    private final Editor contenidoEd = new Editor(true);
    private Foco foco = Foco.TITULO;
    private String inputBusqueda = "";
    private Nota editando = null;        // null = nota nueva
    private String renombrarCarpeta = null; // carpeta que se renombra, null = se renombra una nota
    private State helpVolver = State.LIST;
    private int viewScroll = 0;
    private String aviso = null;         // mensaje breve en la barra de estado

    private List<Nota> papelera = new ArrayList<>();
    private int papelIdx = 0;
    private int papelScroll = 0;

    public void iniciar() throws Exception {
        SwingTerminalFrame frame = new SwingTerminalFrame(
                "notas",
                TerminalEmulatorAutoCloseTrigger.CloseOnExitPrivateMode
        );
        frame.setSize(1020, 660);
        frame.setVisible(true);
        Thread.sleep(100);

        screen = new TerminalScreen(frame);
        screen.startScreen();
        screen.setCursorPosition(null);

        recargar();

        while (true) {
            render();
            KeyStroke key = screen.readInput();
            aviso = null;
            if (!handle(key)) break;
        }

        screen.stopScreen();
        frame.dispose();
    }

    // ── Data ──────────────────────────────────────────────────────────────────

    private void recargar() {
        notas = archivos.loadAll();
        notas.sort((a, b) -> {
            // Favorites first, then by update date
            if (a.isFavorito() != b.isFavorito()) return a.isFavorito() ? -1 : 1;
            return b.getFechaActualizacion().compareTo(a.getFechaActualizacion());
        });
        aplicarFiltro();
    }

    private void aplicarFiltro() {
        carpetas = new ArrayList<>();
        if (inputBusqueda.isEmpty()) {
            if (carpetaActual.isEmpty()) {
                carpetas = notas.stream()
                        .map(Nota::getCarpeta)
                        .filter(c -> !c.isEmpty())
                        .distinct().sorted()
                        .collect(Collectors.toList());
            }
            filtradas = notas.stream()
                    .filter(n -> n.getCarpeta().equals(carpetaActual))
                    .collect(Collectors.toList());
        } else {
            // la búsqueda es global: ignora carpetas
            String q = inputBusqueda.toLowerCase();
            filtradas = notas.stream().filter(n -> coincide(n, q)).collect(Collectors.toList());
        }
        int total = totalEntradas();
        if (selIdx >= total) selIdx = Math.max(0, total - 1);
        if (scroll > selIdx) scroll = selIdx;
    }

    private boolean coincide(Nota n, String q) {
        if (n.getTitulo().toLowerCase().contains(q)) return true;
        if (n.getContenido().toLowerCase().contains(q)) return true;
        List<String> tags = n.getTags();
        return !tags.isEmpty()
                && ("#" + String.join(" #", tags)).toLowerCase().contains(q);
    }

    private int totalEntradas() {
        return carpetas.size() + filtradas.size();
    }

    /** Nota bajo el cursor, o null si el cursor está sobre una carpeta o no hay nada. */
    private Nota notaSel() {
        int i = selIdx - carpetas.size();
        return (i >= 0 && i < filtradas.size()) ? filtradas.get(i) : null;
    }

    // ── Input ─────────────────────────────────────────────────────────────────

    private boolean handle(KeyStroke key) {
        switch (estado) {
            case LIST:          return handleList(key);
            case VIEW:          return handleView(key);
            case EDITOR:        return handleEditor(key);
            case CONFIRM_PURGE: return handleConfirmPurge(key);
            case SEARCH:        return handleSearch(key);
            case TRASH:         return handleTrash(key);
            case MOVE:          return handleMove(key);
            case RENAME:        return handleRename(key);
            case HELP:          estado = helpVolver; return true;
            default:            return true;
        }
    }

    private boolean handleList(KeyStroke key) {
        if (key.getKeyType() == KeyType.Escape) { salirCarpeta(); return true; }
        if (key.getKeyType() == KeyType.Character) {
            char c = key.getCharacter();
            switch (c) {
                case 'q': return false;
                case 'j': navAbajo();  break;
                case 'k': navArriba(); break;
                case 'h': salirCarpeta(); break;
                case 'g': selIdx = 0; scroll = 0; break;
                case 'G': selIdx = Math.max(0, totalEntradas() - 1);
                          scroll = Math.max(0, selIdx - contentRows() + 1); break;
                case 'n': abrirEditor(null); break;
                case 'd': {
                    Nota n = notaSel();
                    if (n != null) {
                        archivos.moverPapelera(n.getId());
                        recargar();
                        aviso = "movida a la papelera — t para abrirla";
                    }
                    break;
                }
                case 'f': toggleFav(); break;
                case 'm': if (notaSel() != null) {
                              promptEd.setTexto(notaSel().getCarpeta());
                              estado = State.MOVE;
                          }
                          break;
                case 'r':
                    if (selIdx < carpetas.size() && !carpetas.isEmpty()) {
                        renombrarCarpeta = carpetas.get(selIdx);
                        promptEd.setTexto(renombrarCarpeta);
                        estado = State.RENAME;
                    } else if (notaSel() != null) {
                        renombrarCarpeta = null;
                        promptEd.setTexto(notaSel().getTitulo());
                        estado = State.RENAME;
                    }
                    break;
                case 't': abrirPapelera(); break;
                case '/': inputBusqueda = ""; estado = State.SEARCH; break;
                case '?': helpVolver = State.LIST; estado = State.HELP; break;
            }
        } else if (key.getKeyType() == KeyType.Enter) {
            if (selIdx < carpetas.size() && !carpetas.isEmpty()) {
                carpetaActual = carpetas.get(selIdx);
                selIdx = 0;
                scroll = 0;
                aplicarFiltro();
            } else if (notaSel() != null) {
                viewScroll = 0;
                estado = State.VIEW;
            }
        } else if (key.getKeyType() == KeyType.ArrowDown) {
            navAbajo();
        } else if (key.getKeyType() == KeyType.ArrowUp) {
            navArriba();
        }
        return true;
    }

    private void salirCarpeta() {
        if (!carpetaActual.isEmpty()) {
            String previa = carpetaActual;
            carpetaActual = "";
            selIdx = 0;
            scroll = 0;
            aplicarFiltro();
            int i = carpetas.indexOf(previa);
            if (i >= 0) {
                selIdx = i;
                scroll = Math.max(0, selIdx - contentRows() + 1);
            }
        }
    }

    private boolean handleView(KeyStroke key) {
        if (key.getKeyType() == KeyType.Escape) { estado = State.LIST; return true; }
        if (key.getKeyType() == KeyType.ArrowDown || key.getKeyType() == KeyType.ArrowUp) {
            viewScroll += (key.getKeyType() == KeyType.ArrowDown) ? 1 : -1;
        } else if (key.getKeyType() == KeyType.PageDown) {
            viewScroll += viewRows();
        } else if (key.getKeyType() == KeyType.PageUp) {
            viewScroll -= viewRows();
        } else if (key.getKeyType() == KeyType.Character) {
            char c = key.getCharacter();
            switch (c) {
                case 'q': estado = State.LIST; return true;
                case 'j': viewScroll++; break;
                case 'k': viewScroll--; break;
                case 'g': viewScroll = 0; break;
                case 'G': viewScroll = Integer.MAX_VALUE; break; // se clampa al renderizar
                case 'y':
                    if (notaSel() != null) {
                        Editor.alPortapapeles(notaSel().getContenido());
                        aviso = "copiado al portapapeles";
                    }
                    break;
                case 'e':
                    if (notaSel() != null) abrirEditor(notaSel());
                    break;
                case '?': helpVolver = State.VIEW; estado = State.HELP; break;
            }
        }
        if (viewScroll < 0) viewScroll = 0;
        return true;
    }

    private void abrirEditor(Nota n) {
        editando = n;
        tituloEd.setTexto(n == null ? "" : n.getTitulo());
        tagsEd.setTexto(n == null || n.getTags().isEmpty()
                ? "" : "#" + String.join(" #", n.getTags()));
        contenidoEd.setTexto(n == null ? "" : n.getContenido());
        foco = (n == null) ? Foco.TITULO : Foco.CUERPO;
        estado = State.EDITOR;
    }

    private boolean handleEditor(KeyStroke key) {
        if (key.getKeyType() == KeyType.Escape) {
            estado = (editando != null) ? State.VIEW : State.LIST;
            return true;
        }
        if (isCtrlS(key)) { guardar(); return true; }

        boolean bajar = key.getKeyType() == KeyType.Enter || key.getKeyType() == KeyType.ArrowDown;
        switch (foco) {
            case TITULO:
                if (bajar) foco = Foco.TAGS;
                else tituloEd.handle(key);
                break;
            case TAGS:
                if (bajar) foco = Foco.CUERPO;
                else if (!tagsEd.handle(key) && key.getKeyType() == KeyType.ArrowUp) foco = Foco.TITULO;
                break;
            case CUERPO:
                if (!contenidoEd.handle(key) && key.getKeyType() == KeyType.ArrowUp) foco = Foco.TAGS;
                break;
        }
        return true;
    }

    private void guardar() {
        String titulo = tituloEd.getTexto().strip();
        List<String> tags = parseTags(tagsEd.getTexto());
        if (editando == null) {
            if (!titulo.isBlank()) {
                Nota n = new Nota(titulo, contenidoEd.getTexto());
                n.setCarpeta(carpetaActual);
                n.setTags(tags);
                archivos.save(n);
                recargar();
            }
            estado = State.LIST;
        } else {
            if (!titulo.isBlank()) editando.setTitulo(titulo);
            editando.setContenido(contenidoEd.getTexto());
            editando.setTags(tags);
            editando.setFechaActualizacion(LocalDate.now());
            archivos.save(editando);
            recargar();
            estado = State.VIEW;
        }
    }

    private List<String> parseTags(String texto) {
        List<String> out = new ArrayList<>();
        for (String t : texto.strip().split("\\s+")) {
            if (t.startsWith("#")) t = t.substring(1);
            if (!t.isEmpty() && !out.contains(t)) out.add(t);
        }
        return out;
    }

    private boolean handleMove(KeyStroke key) {
        if (key.getKeyType() == KeyType.Escape) { estado = State.LIST; return true; }
        if (key.getKeyType() == KeyType.Enter) {
            Nota n = notaSel();
            if (n != null) {
                String destino = promptEd.getTexto().strip();
                n.setCarpeta(destino);
                archivos.save(n);
                recargar();
                aviso = destino.isEmpty() ? "movida a la raíz" : "movida a " + destino + "/";
            }
            estado = State.LIST;
            return true;
        }
        promptEd.handle(key);
        return true;
    }

    private boolean handleRename(KeyStroke key) {
        if (key.getKeyType() == KeyType.Escape) { estado = State.LIST; return true; }
        if (key.getKeyType() == KeyType.Enter) {
            String nuevo = promptEd.getTexto().strip();
            if (renombrarCarpeta != null) {
                // renombrar carpeta = re-etiquetar todas sus notas
                // (nombre vacío las manda a la raíz; uno existente las fusiona)
                for (Nota n : notas) {
                    if (n.getCarpeta().equals(renombrarCarpeta)) {
                        n.setCarpeta(nuevo);
                        archivos.save(n);
                    }
                }
                recargar();
                int i = carpetas.indexOf(nuevo);
                if (i >= 0) { selIdx = i; scroll = Math.min(scroll, selIdx); }
                aviso = nuevo.isEmpty() ? "carpeta disuelta" : "renombrada a " + nuevo + "/";
            } else if (notaSel() != null && !nuevo.isBlank()) {
                Nota n = notaSel();
                n.setTitulo(nuevo);
                n.setFechaActualizacion(LocalDate.now());
                archivos.save(n);
                recargar();
                aviso = "renombrada";
            }
            estado = State.LIST;
            return true;
        }
        promptEd.handle(key);
        return true;
    }

    private void abrirPapelera() {
        papelera = archivos.loadPapelera();
        papelera.sort((a, b) -> b.getFechaActualizacion().compareTo(a.getFechaActualizacion()));
        papelIdx = 0;
        papelScroll = 0;
        estado = State.TRASH;
    }

    private boolean handleTrash(KeyStroke key) {
        if (key.getKeyType() == KeyType.Escape) { estado = State.LIST; return true; }
        if (key.getKeyType() == KeyType.ArrowDown) { papelAbajo(); return true; }
        if (key.getKeyType() == KeyType.ArrowUp)   { papelArriba(); return true; }
        if (key.getKeyType() == KeyType.Character) {
            char c = key.getCharacter();
            switch (c) {
                case 'q': estado = State.LIST; return true;
                case 'j': papelAbajo();  break;
                case 'k': papelArriba(); break;
                case 'r':
                    if (!papelera.isEmpty()) {
                        archivos.restaurar(papelera.get(papelIdx).getId());
                        recargar();
                        refrescarPapelera();
                        aviso = "restaurada";
                    }
                    break;
                case 'd':
                    if (!papelera.isEmpty()) estado = State.CONFIRM_PURGE;
                    break;
            }
        }
        return true;
    }

    private boolean handleConfirmPurge(KeyStroke key) {
        if (key.getKeyType() == KeyType.Character) {
            char c = key.getCharacter();
            if (c == 'y' || c == 's') {
                archivos.eliminarDefinitivo(papelera.get(papelIdx).getId());
                refrescarPapelera();
            }
        }
        estado = State.TRASH;
        return true;
    }

    private void refrescarPapelera() {
        papelera = archivos.loadPapelera();
        papelera.sort((a, b) -> b.getFechaActualizacion().compareTo(a.getFechaActualizacion()));
        if (papelIdx >= papelera.size()) papelIdx = Math.max(0, papelera.size() - 1);
        if (papelScroll > papelIdx) papelScroll = papelIdx;
    }

    private void papelArriba() {
        if (papelIdx > 0) {
            papelIdx--;
            if (papelIdx < papelScroll) papelScroll = papelIdx;
        }
    }

    private void papelAbajo() {
        if (papelIdx < papelera.size() - 1) {
            papelIdx++;
            int h = contentRows();
            if (papelIdx >= papelScroll + h) papelScroll = papelIdx - h + 1;
        }
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
        if (selIdx < totalEntradas() - 1) {
            selIdx++;
            int h = contentRows();
            if (selIdx >= scroll + h) scroll = selIdx - h + 1;
        }
    }

    private void toggleFav() {
        Nota n = notaSel();
        if (n == null) return;
        n.setFavorito(!n.isFavorito());
        archivos.save(n);
        recargar();
    }

    private int contentRows() {
        return screen.getTerminalSize().getRows() - 4;
    }

    private int viewRows() {
        return contentRows() - 3;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────
    //
    // Layout:
    //   row 0          ┌─ notas ──────┬──────────────────────────────┐
    //   rows 1..rows-4 │ list         │ note content                 │
    //   row rows-3     ├──────────────┴──────────────────────────────┤
    //   row rows-2     │ keybindings                                 │
    //   row rows-1     └─────────────────────────────────────────────┘

    private boolean enPapelera() {
        return estado == State.TRASH || estado == State.CONFIRM_PURGE;
    }

    private void render() throws Exception {
        screen.clear();
        int cols = screen.getTerminalSize().getColumns();
        int rows = screen.getTerminalSize().getRows();
        TextGraphics g = screen.newTextGraphics();
        g.setForegroundColor(FG);
        g.setBackgroundColor(BG);

        if (estado == State.EDITOR) {
            renderEditor(g, cols, rows);
        } else if (estado == State.HELP) {
            renderHelp(g, cols, rows);
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
        g.setForegroundColor(FG);

        // Top border
        g.setCharacter(0, 0, Symbols.SINGLE_LINE_TOP_LEFT_CORNER);
        g.setCharacter(cols - 1, 0, Symbols.SINGLE_LINE_TOP_RIGHT_CORNER);
        hline(g, 1, cols - 2, 0);
        String titulo = enPapelera() ? " papelera "
                : carpetaActual.isEmpty() ? " notas "
                : " notas/" + carpetaActual + " ";
        if (titulo.length() > listW - 4) titulo = titulo.substring(0, listW - 5) + "… ";
        g.putString(4, 0, titulo);
        g.setCharacter(listW + 1, 0, Symbols.SINGLE_LINE_T_DOWN);

        // Side borders
        vline(g, 0, 1, rows - 2);
        vline(g, cols - 1, 1, rows - 2);

        // Vertical divider
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
        List<Nota> fuente = enPapelera() ? papelera : filtradas;
        int nCarpetas = enPapelera() ? 0 : carpetas.size();
        int sel = enPapelera() ? papelIdx : selIdx;
        int scr = enPapelera() ? papelScroll : scroll;
        int height = contentRows();

        if (estado == State.SEARCH) {
            g.setForegroundColor(FG_FAV);
            String bar = "/ " + inputBusqueda + "█";
            if (bar.length() > listW - 1) bar = bar.substring(0, listW - 1);
            g.putString(1, 1, bar);
            g.setForegroundColor(FG);
        }

        int startRow = (estado == State.SEARCH) ? 2 : 1;
        int visible  = (estado == State.SEARCH) ? height - 1 : height;
        int titleMax = listW - 9; // 3 prefix + 1 space + 5 date

        for (int i = 0; i < visible; i++) {
            int idx = scr + i;
            if (idx >= nCarpetas + fuente.size()) break;
            int row = startRow + i;
            boolean esSel = (idx == sel);

            String line;
            TextColor color = FG;
            if (idx < nCarpetas) {
                String nombre = carpetas.get(idx) + "/";
                long cuenta = cuentaNotas(carpetas.get(idx));
                if (nombre.length() > titleMax) nombre = nombre.substring(0, titleMax - 1) + "…";
                line = String.format("   %-" + titleMax + "s %4d", nombre, cuenta);
                color = FG_DIM;
            } else {
                Nota n = fuente.get(idx - nCarpetas);
                String fav  = n.isFavorito() ? "★" : " ";
                String date = n.getFechaActualizacion().format(DATE_FMT);
                String title = n.getTitulo();
                if (title.length() > titleMax) title = title.substring(0, titleMax - 1) + "…";
                line = String.format(" %s %-" + titleMax + "s %s", fav, title, date);
                if (n.isFavorito()) color = FG_FAV;
            }
            if (line.length() > listW) line = line.substring(0, listW);

            if (esSel) {
                g.setForegroundColor(SEL_FG);
                g.setBackgroundColor(SEL_BG);
            } else {
                g.setForegroundColor(color);
            }
            g.putString(1, row, line);
            g.setForegroundColor(FG);
            g.setBackgroundColor(BG);
        }

        int total = nCarpetas + fuente.size();
        if (total > 0) {
            g.setForegroundColor(FG_DIM);
            g.putString(1, rows - 4, " " + (sel + 1) + "/" + total);
            g.setForegroundColor(FG);
        }
    }

    private long cuentaNotas(String carpeta) {
        return notas.stream().filter(n -> n.getCarpeta().equals(carpeta)).count();
    }

    private void renderNote(TextGraphics g, int cols, int rows, int listW) {
        int startCol = listW + 2;
        int contentW = cols - startCol - 1;
        int maxLines = viewRows();

        Nota n;
        if (enPapelera()) {
            n = papelera.isEmpty() ? null : papelera.get(papelIdx);
        } else if (selIdx < carpetas.size() && !carpetas.isEmpty()) {
            // cursor sobre una carpeta: resumen
            g.setForegroundColor(FG_DIM);
            String msg = carpetas.get(selIdx) + "/  —  " + cuentaNotas(carpetas.get(selIdx)) + " notas";
            int mx = startCol + (contentW - msg.length()) / 2;
            g.putString(Math.max(startCol, mx), rows / 2, msg);
            g.setForegroundColor(FG);
            return;
        } else {
            n = notaSel();
        }

        if (n == null) {
            g.setForegroundColor(FG_DIM);
            String msg = enPapelera() ? "papelera vacía" : "sin notas  —  n para crear";
            int mx = startCol + (contentW - msg.length()) / 2;
            g.putString(Math.max(startCol, mx), rows / 2, msg);
            g.setForegroundColor(FG);
            return;
        }

        // Title
        g.setForegroundColor(FG_HI);
        String title = n.getTitulo();
        if (title.length() > contentW) title = title.substring(0, contentW - 1) + "…";
        g.putString(startCol, 1, title);

        // Underline
        g.setForegroundColor(FG);
        int sepLen = Math.min(title.length() + 2, contentW);
        StringBuilder sep = new StringBuilder();
        for (int i = 0; i < sepLen; i++) sep.append(Symbols.SINGLE_LINE_HORIZONTAL);
        g.putString(startCol, 2, sep.toString());

        // Content: markdown ligero + word-wrap + scroll
        List<Linea> cuerpo = markdown(n.getContenido(), contentW);
        int maxScroll = Math.max(0, cuerpo.size() - maxLines);
        int vs = 0;
        if (estado == State.VIEW) {
            if (viewScroll > maxScroll) viewScroll = maxScroll;
            vs = viewScroll;
        }
        for (int i = 0; i < maxLines && vs + i < cuerpo.size(); i++) {
            Linea l = cuerpo.get(vs + i);
            g.setForegroundColor(l.color);
            g.putString(startCol, 3 + i, l.texto);
        }
        g.setForegroundColor(FG);

        // Meta
        g.setForegroundColor(FG_DIM);
        String meta = n.getFechaCreacion().toString();
        if (!n.getTags().isEmpty()) meta += "  #" + String.join("  #", n.getTags());
        if (maxScroll > 0) {
            int pct = (int) Math.round(100.0 * (vs + maxLines) / cuerpo.size());
            meta += "  " + Math.min(100, pct) + "%";
        }
        if (meta.length() > contentW) meta = meta.substring(0, contentW - 1);
        g.putString(startCol, rows - 4, meta);
        g.setForegroundColor(FG);
    }

    // ── Markdown ligero ───────────────────────────────────────────────────────
    //
    //   # titulo        → blanco
    //   - item          → • item
    //   - [ ] / - [x]   → casilla (hecha en tenue)
    //   > cita          → │ cita, tenue
    //   ```             → bloque de código en blanco

    private static class Linea {
        final String texto;
        final TextColor color;
        Linea(String texto, TextColor color) { this.texto = texto; this.color = color; }
    }

    private List<Linea> markdown(String contenido, int ancho) {
        List<Linea> out = new ArrayList<>();
        boolean codigo = false;
        for (String cruda : contenido.split("\n", -1)) {
            String s = cruda.stripLeading();
            String indent = cruda.substring(0, cruda.length() - s.length());

            if (s.startsWith("```")) {
                codigo = !codigo;
                envolver(out, cruda, ancho, FG_DIM);
                continue;
            }
            if (codigo) {
                envolver(out, cruda, ancho, FG_HI);
                continue;
            }

            TextColor color = FG;
            String texto = cruda;
            if (s.startsWith("# "))        { texto = s.substring(2); color = FG_HI; }
            else if (s.startsWith("## "))  { texto = s.substring(3); color = FG_HI; }
            else if (s.startsWith("### ")) { texto = s.substring(4); color = FG_HI; }
            else if (s.startsWith("- [x] ") || s.startsWith("- [X] "))
                                           { texto = indent + "[x] " + s.substring(6); color = FG_DIM; }
            else if (s.startsWith("- [ ] "))
                                           { texto = indent + "[ ] " + s.substring(6); }
            else if (s.startsWith("- ") || s.startsWith("* "))
                                           { texto = indent + "• " + s.substring(2); }
            else if (s.startsWith("> "))   { texto = indent + "│ " + s.substring(2); color = FG_DIM; }
            envolver(out, texto, ancho, color);
        }
        return out;
    }

    private void envolver(List<Linea> out, String linea, int ancho, TextColor color) {
        if (linea.isEmpty()) { out.add(new Linea("", color)); return; }
        while (linea.length() > ancho) {
            int corte = linea.lastIndexOf(' ', ancho);
            if (corte <= 0) corte = ancho; // palabra más larga que el ancho: cortar
            out.add(new Linea(linea.substring(0, corte), color));
            linea = linea.substring(corte).stripLeading();
        }
        out.add(new Linea(linea, color));
    }

    private void renderStatus(TextGraphics g, int cols, int rows) {
        g.setForegroundColor(FG_DIM);

        if (estado == State.MOVE || estado == State.RENAME) {
            String prompt = (estado == State.MOVE) ? "  carpeta: " : "  nombre: ";
            g.setForegroundColor(FG_FAV);
            g.putString(1, rows - 2, prompt);
            promptEd.render(g, 1 + prompt.length(), rows - 2, cols - 3 - prompt.length(), 1, true);
            g.setForegroundColor(FG);
            return;
        }

        String s;
        if (aviso != null) {
            s = "  " + aviso;
        } else {
            switch (estado) {
                case VIEW:          s = "  e editar  y copiar  ? ayuda  esc volver"; break;
                case CONFIRM_PURGE: s = "  eliminar definitivamente?  s/y confirmar  otra tecla cancela"; break;
                case SEARCH:        s = "  esc cancelar  enter confirmar"; break;
                case TRASH:         s = "  r restaurar  d eliminar  esc volver"; break;
                default:
                    s = carpetaActual.isEmpty()
                        ? "  enter abrir  n nueva  / buscar  ? ayuda  q salir"
                        : "  h volver  enter abrir  n nueva  ? ayuda  q salir";
                    break;
            }
        }
        if (s.length() > cols - 2) s = s.substring(0, cols - 2);
        g.putString(1, rows - 2, s);
        g.setForegroundColor(FG);
    }

    private void renderEditor(TextGraphics g, int cols, int rows) {
        g.setForegroundColor(FG);

        // Frame
        g.setCharacter(0, 0, Symbols.SINGLE_LINE_TOP_LEFT_CORNER);
        g.setCharacter(cols - 1, 0, Symbols.SINGLE_LINE_TOP_RIGHT_CORNER);
        hline(g, 1, cols - 2, 0);
        g.putString(4, 0, editando != null ? " editar nota " : " nueva nota ");
        vline(g, 0, 1, rows - 2);
        vline(g, cols - 1, 1, rows - 2);
        g.setCharacter(0, rows - 3, Symbols.SINGLE_LINE_T_RIGHT);
        g.setCharacter(cols - 1, rows - 3, Symbols.SINGLE_LINE_T_LEFT);
        hline(g, 1, cols - 2, rows - 3);
        g.setCharacter(0, rows - 1, Symbols.SINGLE_LINE_BOTTOM_LEFT_CORNER);
        g.setCharacter(cols - 1, rows - 1, Symbols.SINGLE_LINE_BOTTOM_RIGHT_CORNER);
        hline(g, 1, cols - 2, rows - 1);

        // Title field
        g.setForegroundColor(FG_DIM);
        g.putString(2, 1, "titulo:");
        g.setForegroundColor(foco == Foco.TITULO ? FG_HI : FG);
        tituloEd.render(g, 10, 1, cols - 12, 1, foco == Foco.TITULO);

        // Tags field
        g.setForegroundColor(FG_DIM);
        g.putString(2, 2, "tags:");
        g.setForegroundColor(foco == Foco.TAGS ? FG_HI : FG_DIM);
        tagsEd.render(g, 10, 2, cols - 12, 1, foco == Foco.TAGS);

        // Divider
        g.setForegroundColor(FG);
        hline(g, 2, cols - 3, 3);

        // Content
        contenidoEd.render(g, 2, 4, cols - 4, rows - 8, foco == Foco.CUERPO);

        // Status
        g.setForegroundColor(FG_DIM);
        String hint;
        switch (foco) {
            case TITULO: hint = "  enter/↓ a tags  ctrl+s guardar  esc cancelar"; break;
            case TAGS:   hint = "  #tags con espacios  enter/↓ al contenido"; break;
            default:     hint = "  ctrl+s guardar  ctrl+z deshacer  esc cancelar"; break;
        }
        g.putString(1, rows - 2, hint);
        if (foco == Foco.CUERPO) {
            String pos = (contenidoEd.getFila() + 1) + ":" + (contenidoEd.getCol() + 1) + " ";
            g.putString(cols - 1 - pos.length(), rows - 2, pos);
        }
        g.setForegroundColor(FG);
    }

    private static final String[] AYUDA_IZQ = {
            "lista",
            "  j/k ↑/↓     navegar",
            "  g/G         inicio / final",
            "  enter       abrir nota o carpeta",
            "  h, esc      salir de la carpeta",
            "  n           nueva nota",
            "  d           borrar (a la papelera)",
            "  f           favorito",
            "  m           mover a carpeta",
            "  r           renombrar nota o carpeta",
            "  t           papelera",
            "  /           buscar (global, #tag tambien)",
            "  q           salir",
            "",
            "papelera",
            "  r           restaurar",
            "  d           eliminar definitivo",
            "  esc, q      volver",
    };

    private static final String[] AYUDA_DER = {
            "vista",
            "  j/k ↑/↓     scroll",
            "  g/G         inicio / final",
            "  e           editar",
            "  y           copiar al portapapeles",
            "  esc, q      volver",
            "",
            "editor",
            "  enter/↓, ↑  moverse entre campos",
            "  ctrl+s      guardar",
            "  ctrl+z      deshacer",
            "  ctrl+v      pegar",
            "  ctrl+k      cortar linea",
            "  ctrl+←/→    saltar palabra",
            "  tab         4 espacios",
            "  esc         cancelar",
            "",
            "markdown:  # titulo   - lista   - [ ] tarea   > cita   ```codigo```",
    };

    private void renderHelp(TextGraphics g, int cols, int rows) {
        g.setForegroundColor(FG);

        // Frame
        g.setCharacter(0, 0, Symbols.SINGLE_LINE_TOP_LEFT_CORNER);
        g.setCharacter(cols - 1, 0, Symbols.SINGLE_LINE_TOP_RIGHT_CORNER);
        hline(g, 1, cols - 2, 0);
        g.putString(4, 0, " ayuda ");
        vline(g, 0, 1, rows - 2);
        vline(g, cols - 1, 1, rows - 2);
        g.setCharacter(0, rows - 1, Symbols.SINGLE_LINE_BOTTOM_LEFT_CORNER);
        g.setCharacter(cols - 1, rows - 1, Symbols.SINGLE_LINE_BOTTOM_RIGHT_CORNER);
        hline(g, 1, cols - 2, rows - 1);

        int colIzq = 3;
        int colDer = Math.max(cols / 2 + 1, 40);
        pintarAyuda(g, AYUDA_IZQ, colIzq, 2, colDer - colIzq - 2, rows);
        pintarAyuda(g, AYUDA_DER, colDer, 2, cols - colDer - 2, rows);

        g.setForegroundColor(FG_DIM);
        g.putString(3, rows - 2, "cualquier tecla para volver");
        g.setForegroundColor(FG);
    }

    private void pintarAyuda(TextGraphics g, String[] lineas, int x, int y, int ancho, int rows) {
        for (int i = 0; i < lineas.length && y + i < rows - 2; i++) {
            String l = lineas[i];
            if (l.length() > ancho) l = l.substring(0, ancho);
            // los encabezados de sección van sin sangría
            boolean encabezado = !l.isEmpty() && !l.startsWith(" ");
            g.setForegroundColor(encabezado ? FG_HI : FG);
            g.putString(x, y + i, l);
        }
        g.setForegroundColor(FG);
    }

    // ── Primitives ────────────────────────────────────────────────────────────

    private void hline(TextGraphics g, int startCol, int endCol, int row) {
        for (int c = startCol; c <= endCol; c++) g.setCharacter(c, row, Symbols.SINGLE_LINE_HORIZONTAL);
    }

    private void vline(TextGraphics g, int col, int startRow, int endRow) {
        for (int r = startRow; r <= endRow; r++) g.setCharacter(col, r, Symbols.SINGLE_LINE_VERTICAL);
    }
}
