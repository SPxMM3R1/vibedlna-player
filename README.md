# VibeDLNA Player

[![Android CI](https://github.com/SPxMM3R1/vibedlna-player/actions/workflows/android-ci.yml/badge.svg)](https://github.com/SPxMM3R1/vibedlna-player/actions/workflows/android-ci.yml)

Reproductor DLNA para Android TV con la interfaz compacta de VibeM3U.

## Funcionamiento

- Descubre automáticamente los MediaServers DLNA/UPnP anunciados en la red local.
- Permite elegir un servidor y navegar sus carpetas virtuales mediante
  `ContentDirectory:Browse` ejecutado por jUPnP.
- Guarda miniaturas en almacenamiento privado persistente y permite usar la
  imagen anunciada por el servidor o generar fotogramas al 25%, 50% o 75%.
- Muestra las carpetas en tarjetas compactas y los videos en tarjetas 16:9.
- Conserva temporalmente la posición de un video durante una hora para
  reanudarlo automáticamente; no mantiene historial permanente.

## Controles

- Flechas y `OK`: recorrer carpetas y abrir videos.
- `Menú` o `Configuración`: abrir las opciones.
- En el reproductor, izquierda/derecha: retroceder o adelantar y mostrar la
  barra de progreso.
- Pausa/reanudación: mostrar la barra de progreso.
- `Atrás` en el reproductor: ocultar primero la interfaz y, al pulsarlo otra
  vez, cerrar el video.
- `Atrás` en la biblioteca: subir una carpeta o confirmar la salida.

## Actualizaciones

La app consulta la última Release pública de GitHub al abrirse. Si existe una
versión superior, descarga el APK, comprueba el paquete y la firma y solicita
la confirmación de Android para instalarlo.

GitHub Actions ejecuta las pruebas, Android Lint y la compilación. Las etiquetas
`vX.Y.Z` publican automáticamente una APK firmada en Releases.
