package com.notas.datos;

import com.google.gson.*;
import com.notas.model.Nota;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Archivos {

    private final Path storageDir;
    private final Gson gson;

    public Archivos() {
        this.storageDir = Path.of(System.getProperty("user.home"), ".notas");
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
        List<Nota> notas = new ArrayList<>();
        try {
            Files.createDirectories(storageDir);
            Files.list(storageDir)
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

    public Path getStorageDir() { return storageDir; }

    public void moverPapelera(String id) {}
    public List<Nota> loadPapelera() { return new ArrayList<>(); }
    public void restaurar(String id) {}
    public void eliminarDefinitivo(String id) {}
}
