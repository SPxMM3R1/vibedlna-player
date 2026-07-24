# VibeM3U

[![Android CI](https://github.com/SPxMM3R1/vibem3u/actions/workflows/android-ci.yml/badge.svg)](https://github.com/SPxMM3R1/vibem3u/actions/workflows/android-ci.yml)

Reproductor M3U para Android TV inspirado en la experiencia de un set-top box:
abre directamente la señal, descarga la lista nuevamente en cada inicio y muestra
una barra compacta al cambiar de canal.

La versión 0.2.1 añade navegación configurable, caché persistente de logos,
confirmación de salida y programación actual desde la EPG XMLTV declarada por la M3U.

## Descargar

- [Descargar VibeM3U v0.2.1](https://github.com/SPxMM3R1/vibem3u/releases/download/v0.2.1/VibeM3U-v0.2.1.apk)
- [Ver todas las versiones](https://github.com/SPxMM3R1/vibem3u/releases)

## Controles

- `↑` / `↓`: canal anterior o siguiente; el sentido puede invertirse en Configuración.
- `OK`: mostrar la información del canal.
- Mantener `OK`, botón `Menú` o botón `Configuración`: editar la URL M3U.
- `Atrás`: confirmar salida o volver desde Configuración.

## Compilación automática

GitHub Actions realiza toda la compilación:

- Cada cambio enviado a `main` ejecuta las pruebas, el análisis de Android y genera
  un APK descargable desde la ejecución de **Android CI**.
- Cada etiqueta con formato `vX.Y.Z` vuelve a compilar el proyecto en GitHub y
  publica el APK en **Releases**.
- La firma se recupera desde un secreto cifrado de GitHub para que el APK pueda
  actualizar instalaciones existentes.

Para publicar una nueva versión, primero actualiza `versionCode` y `versionName`,
y luego crea una etiqueta que coincida con la versión, por ejemplo `v0.3.0`.
