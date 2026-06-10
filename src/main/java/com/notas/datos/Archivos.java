package com.notas.datos;

import com.google.gson.*;
import com.notas.model.Nota;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Archivos {

    private final Path storageDir;
    private final Path papeleraDir;
    private final Gson gson;

    public Archivos() {
        this.storageDir = Path.of(System.getProperty("user.home"), ".notas");
        this.papeleraDir = storageDir.resolve("papelera");
        this.gson = new GsonBuilder()
                .registerTypeAdapter(LocalDate.class,
                        (JsonSerializer<LocalDate>) (src, t, ctx) -> new JsonPrimitive(src.toString()))
                .registerTypeAdapter(LocalDate.class,
                        (JsonDeserializer<LocalDate>) (json, t, ctx) -> LocalDate.parse(json.getAsString()))
                .create();
    }

    public void save(Nota nota) {
        try {
            Files.createDirectories(storageDir);
            Files.writeString(storageDir.resolve(nota.getId() + ".json"), gson.toJson(nota));
        } catch (IOException e) {
            System.err.println("Error al guardar: " + e.getMessage());
        }
    }

    public Nota load(String id) {
        try {
            Path file = storageDir.resolve(id + ".json");
            if (!Files.exists(file)) return null;
            return gson.fromJson(Files.readString(file), Nota.class);
        } catch (IOException e) {
            System.err.println("Error al cargar: " + e.getMessage());
            return null;
        }
    }

    public List<Nota> loadAll() {
        return loadDir(storageDir);
    }

    public List<Nota> loadPapelera() {
        return loadDir(papeleraDir);
    }

    private List<Nota> loadDir(Path dir) {
        List<Nota> notas = new ArrayList<>();
        try {
            Files.createDirectories(dir);
            Files.list(dir)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> {
                        try {
                            notas.add(gson.fromJson(Files.readString(p), Nota.class));
                        } catch (IOException e) {
                            System.err.println("Error leyendo: " + p.getFileName());
                        }
                    });
        } catch (IOException e) {
            System.err.println("Error al cargar notas: " + e.getMessage());
        }
        return notas;
    }

    public void delete(String id) {
        try {
            Files.deleteIfExists(storageDir.resolve(id + ".json"));
        } catch (IOException e) {
            System.err.println("Error al borrar: " + e.getMessage());
        }
    }

    public void moverPapelera(String id) {
        mover(storageDir, papeleraDir, id);
    }

    public void restaurar(String id) {
        mover(papeleraDir, storageDir, id);
    }

    public void eliminarDefinitivo(String id) {
        try {
            Files.deleteIfExists(papeleraDir.resolve(id + ".json"));
        } catch (IOException e) {
            System.err.println("Error al eliminar: " + e.getMessage());
        }
    }

    private void mover(Path desde, Path hacia, String id) {
        try {
            Files.createDirectories(hacia);
            Files.move(desde.resolve(id + ".json"), hacia.resolve(id + ".json"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            System.err.println("Error al mover: " + e.getMessage());
        }
    }

    public Path getStorageDir() { return storageDir; }
}
