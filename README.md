# VibeDLNA Player

[![Android CI](https://github.com/SPxMM3R1/vibedlna-player/actions/workflows/android-ci.yml/badge.svg)](https://github.com/SPxMM3R1/vibedlna-player/actions/workflows/android-ci.yml)

Reproductor de carpetas de video para Android TV con la interfaz compacta de
VibeM3U.

## Funcionamiento

- Selecciona una carpeta mediante el selector seguro de Android.
- Conserva únicamente el permiso y el nombre de esa carpeta.
- Examina la carpeta y sus subcarpetas automáticamente al abrir la app.
- Genera y guarda en caché una miniatura del fotograma situado al 50% de cada video.
- Reproduce siempre desde el comienzo.
- No guarda posición, progreso, historial, vistos ni último video seleccionado.

## Controles

- Flechas y `OK`: recorrer y abrir videos.
- `Menú` o `Configuración`: abrir las opciones.
- `Atrás`: cerrar opciones, volver a la biblioteca o confirmar la salida.

## Actualizaciones

La app consulta la última Release pública de GitHub al abrirse. Si existe una
versión superior, descarga el APK, comprueba el paquete y la firma y solicita la
confirmación de Android para instalarlo.

GitHub Actions ejecuta las pruebas, Android Lint y la compilación. Las etiquetas
`vX.Y.Z` publican automáticamente una APK firmada en Releases.
