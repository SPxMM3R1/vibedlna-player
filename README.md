# VibeDLNA Player

[![Android CI](https://github.com/SPxMM3R1/vibedlna-player/actions/workflows/android-ci.yml/badge.svg)](https://github.com/SPxMM3R1/vibedlna-player/actions/workflows/android-ci.yml)

Reproductor DLNA para Android TV con la interfaz compacta de VibeM3U.

## Funcionamiento

- Descubre automáticamente los MediaServers DLNA/UPnP anunciados en la red local.
- Busca por SSDP multicast y broadcast de la subred para mantener compatibilidad
  con [VibeDLNA](https://github.com/SPxMM3R1/VibeDLNA) y redes que filtran multicast.
- Permite elegir un servidor y navegar sus carpetas virtuales mediante
  `ContentDirectory:Browse`.
- Conserva únicamente el identificador del servidor y la carpeta remota elegida.
- Al abrir la app vuelve a descubrir la red y reconecta el servidor guardado sólo
  cuando está disponible.
- Genera y guarda en caché una miniatura del fotograma situado al 50% de cada video
  remoto.
- Reproduce siempre desde el comienzo.
- No guarda posición, progreso, historial, vistos ni último video seleccionado.

## Controles

- Flechas y `OK`: recorrer carpetas y abrir videos.
- `Menú` o `Configuración`: cambiar servidor o volver a examinar la red.
- `Atrás`: cerrar opciones, subir una carpeta o confirmar la salida.

## Actualizaciones

La app consulta la última Release pública de GitHub al abrirse. Si existe una
versión superior, descarga el APK, comprueba el paquete y la firma y solicita la
confirmación de Android para instalarlo.

GitHub Actions ejecuta las pruebas, Android Lint y la compilación. Las etiquetas
`vX.Y.Z` publican automáticamente una APK firmada en Releases.
