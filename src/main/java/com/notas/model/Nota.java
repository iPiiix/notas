package com.notas.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class Nota {

      private String id;
      private String titulo;
      private String contenido;
      private boolean favorito;
      private LocalDate fechaCreacion;
      private LocalDate fechaActualizacion;
      private List<String> tags;

      // Constructor completo (para cargar desde fichero)
      public Nota(String id, String titulo, String contenido, boolean favorito,
                  LocalDate fechaCreacion, LocalDate fechaActualizacion, List<String> tags) {
            this.id = id;
            this.titulo = titulo;
            this.contenido = contenido;
            this.favorito = favorito;
            this.fechaCreacion = fechaCreacion;
            this.fechaActualizacion = fechaActualizacion;
            this.tags = tags;
      }

      // Constructor simple (para crear nota nueva)
      public Nota(String titulo, String contenido) {
            this.id = java.util.UUID.randomUUID().toString();
            this.titulo = titulo;
            this.contenido = contenido;
            this.favorito = false;
            this.fechaCreacion = LocalDate.now();
            this.fechaActualizacion = LocalDate.now();
            this.tags = new ArrayList<>();
      }

      public String getId() { return id; }
      public String getTitulo() { return titulo; }
      public void setTitulo(String titulo) { this.titulo = titulo; }
      public String getContenido() { return contenido; }
      public void setContenido(String contenido) { this.contenido = contenido; }
      public boolean isFavorito() { return favorito; }
      public void setFavorito(boolean favorito) { this.favorito = favorito; }
      public LocalDate getFechaCreacion() { return fechaCreacion; }
      public LocalDate getFechaActualizacion() { return fechaActualizacion; }
      public void setFechaActualizacion(LocalDate fecha) { this.fechaActualizacion = fecha; }
      public List<String> getTags() { return tags; }
}
