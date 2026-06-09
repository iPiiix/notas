# notas

Terminal notes app. Keyboard only.

```
┌─ notas ──────────────────────────────────────────────────────────┐
│ ★ ideas proyecto      06-09 │ ideas proyecto                     │
│   compras             06-08 │ ─────────────                      │
│   daily               06-07 │ - refactorizar auth                │
│ 1/3                         │ - revisar PRs pendientes           │
├─────────────────────────────┴────────────────────────────────────┤
│  j/k nav  enter ver  n nueva  d borrar  f fav  / buscar  q salir │
└──────────────────────────────────────────────────────────────────┘
```

## build

```
mvn package
```

## run

```
notas.bat
```

requires JDK 11+

## keys

```
j / k       navegar lista
enter       abrir nota
n           nueva nota
e           editar
d           borrar
f           favorito
/           buscar
q           salir
```

## storage

`~/.notas/*.json`
