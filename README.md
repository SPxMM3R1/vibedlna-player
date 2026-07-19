# StreamBox TV

Reproductor M3U para Android TV inspirado en la experiencia de un set-top box:
abre directamente la señal, descarga la lista nuevamente en cada inicio y muestra
una barra compacta al cambiar de canal.

## Descargar

- [StreamBoxTV v0.1.0 para Android TV](https://gitlab.com/roberto.ramos.dz/streambox-tv/-/raw/main/releases/StreamBoxTV-v0.1.0.apk)

## Instalar con Downloader

1. Abre Downloader en Android TV.
2. Escribe el código **7068069**.
3. Descarga e instala el APK.

Enlace corto equivalente: [aftv.news/7068069](https://aftv.news/7068069).

## Controles

- `↑` / `↓`: canal anterior o siguiente.
- `OK`: mostrar la información del canal.
- Mantener `OK`, botón `Menú` o botón `Configuración`: editar la URL M3U.
- `Atrás`: salir o volver desde Configuración.

## Compilar

En Windows, con Android SDK y JDK configurados:

```powershell
.\gradlew.bat assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.
