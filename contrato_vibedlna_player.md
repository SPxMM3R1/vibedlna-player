# contrato vibedlna <-> vibedlna player

archivo identico en ambos repositorios. si cambias un lado del contrato, actualiza este archivo en los dos y agrega pruebas en los dos.

- servidor: VibeDLNA, Windows, WinForms .NET 8, repo SPxMM3R1/VibeDLNA, base v1.0.8 / e02d75f.
- cliente: VibeDLNA Player, Android TV, Java + jUPnP + Media3, repo SPxMM3R1/vibedlna-player, base v0.3.15 / df20f23.
- son un mismo producto con dos repositorios: commits, tags y releases separados; el contrato es comun.
- VibeM3U y VibeSubtitle no participan en este contrato.

## 1. descubrimiento e identidad

| aspecto | servidor | player |
|---|---|---|
| tipo | `urn:schemas-upnp-org:device:MediaServer:1` | busca MediaServer y servicio ContentDirectory |
| identidad | `UDN uuid:<AppSettings.Uuid>`, persistido en settings.json | identifica por UDN, nunca por friendlyName |
| SSDP | alive cada 60 s, max-age 1800, byebye al detener | busqueda periodica y remove por byebye/expiracion |
| descripcion | `/description.xml` | via jUPnP |

el UUID no debe regenerarse: si cambia, el player lo ve como otro servidor y pierde cache de miniaturas y reanudado.

## 2. browse (ContentDirectory:1)

- acciones: `BrowseDirectChildren` y `BrowseMetadata`, con `StartingIndex`/`RequestedCount`; `RequestedCount=0` devuelve todo.
- respuesta: `NumberReturned` = elementos de la pagina, `TotalMatches` = total de la carpeta, `UpdateID`.
- el player pide paginas de 8, maximo 2000 elementos, timeout 15 s por pagina; si una pagina falla reintenta item por item.
- el player usa `BrowseMetadata` para obtener `parentID`; `-1` o vacio se trata como raiz `0`.
- el orden de hijos debe ser estable entre paginas (servidor: carpetas y luego archivos, ordenados por nombre).

## 3. object id

- `0` raiz; `R:<claveRaiz>` carpeta compartida; `D:<base64url(claveRaiz|rutaRelativa)>` carpeta; `F:<...>` archivo.
- `claveRaiz` = 16 hex del SHA-256 de la ruta completa de la carpeta compartida (mayusculas, sin separador final). no depende de la posicion: agregar, quitar o reordenar carpetas no cambia los ids de las demas.
- los ids antiguos con indice numerico (`R:0`, `0|ruta`) siguen resolviendo mientras la lista de carpetas no cambie.
- renombrar o mover una carpeta compartida si cambia sus ids: es otra ruta.
- el player usa el id tal cual como identidad: `VideoItem.stableKey = udn + id`, que alimenta la cache de miniaturas y el reanudado.

## 4. items y recursos

servidor emite por archivo:

```xml
<item id="F:..." parentID="..." restricted="1">
  <dc:title>nombre visible</dc:title>
  <upnp:class>object.item.videoItem</upnp:class>
  <res protocolInfo="http-get:*:video/mp4:DLNA.ORG_OP=01;DLNA.ORG_FLAGS=0170..." size="N">
    http://<ip>:<puerto>/media/<id escapado>/<nombre escapado>
  </res>
  <upnp:albumArtURI>http://<ip>:<puerto>/thumbnail/<sha256>.jpg</upnp:albumArtURI>  <!-- solo si esta listo -->
</item>
```

- el player solo muestra items con MIME `video/*` o clase `videoItem` y `res` http/https. audio e imagenes que el servidor comparta se ignoran en el player.
- `duration` (`H:MM:SS.mmm`) se envia solo cuando el warmup ya la midio con la propiedad System.Media.Duration de Windows; Browse nunca abre el archivo. el player la lee con `durationMillis` y la pasa al reproductor. desde 1.1.0 tambien `resolution`, `bitrate`, `nrAudioChannels` y `dc:description` cuando el warmup ya los midio (§7).
- `DLNA.ORG_OP=01` promete rangos: el servidor responde `Range` con 206, `Accept-Ranges: bytes` y `transferMode.dlna.org: Streaming`. el seek del player depende de esto.
- el servidor no transcodifica; el contenedor y codec deben ser soportados por Media3 en la TV.
- nombres con espacios: URL escapada (`%20`), `dc:title` legible.

## 5. miniaturas (regla central)

- puerto HTTP fijo `8210` (`DlnaServer.DefaultPort`); settings con `Port: 0` migran a 8210. si el puerto esta ocupado, el servidor usa uno libre y lo registra en el log.
- servidor: genera en segundo plano con Windows Shell, cache por SHA-256 del contenido, sirve `/thumbnail/<sha256>.jpg` con `Cache-Control: public, max-age=31536000, immutable`. anuncia `upnp:albumArtURI` solo cuando el JPG existe. Browse nunca hashea ni genera.
- player: usa solo el `upnp:albumArtURI` anunciado; resuelve relativas contra el origen del recurso; solo http/https. nunca genera frames, nunca deriva endpoints, nunca pide generacion. sin artwork: placeholder.
- player: descarga con la URL anunciada completa, pero la clave de cache es udn + id + ruta exacta de esa URL (sin esquema, host ni puerto), asi un cambio de IP o puerto no invalida la cache. la cache en disco se poda a 2000 archivos, primero los menos usados.
- no existe `/thumbnail/request/...` en ningun lado. el servidor lo protege con pruebas (CoreTests); el player lo cumple por diseno (no construye URLs de miniatura) pero no tiene una prueba explicita.
- un item que hoy no trae artwork puede traerlo en un browse posterior cuando el warmup termine.

## 6. historial del contrato

- 2026-10-06: resueltos los tres riesgos detectados: puerto fijo 8210 con respaldo, ids por clave de ruta, `duration` medida en el warmup; el player cachea por ruta de artwork y poda su cache en disco. publicado como servidor v1.0.9 y player v0.3.16 (etiquetas v1.0.9 / v0.3.16).
- 2026-11-14: ampliacion 1.1.0 del servidor (§7): datos tecnicos por item, pie de la raiz, identidad del reproductor por User-Agent y actividad (viendo ahora, seguir viendo, reproductores vistos). servidor en desarrollo, sin publicar. el player la implementa en su repo (§7 del player pendiente).

cualquier cambio futuro del contrato: decidirlo con el usuario, implementarlo en el lado que corresponda, agregar pruebas en ambos repos y actualizar este archivo en los dos.

## 7. ampliacion (1.1.0)

todo es opcional: un cliente que no lo lee sigue funcionando. el servidor lo envia cuando lo sabe; si falta un dato, se omite.

- A. datos tecnicos de cada video o audio. el calentamiento lee las propiedades `System.Video.*` y `System.Audio.*` del Shell junto con la duracion, en la misma entrada de la cache (por sha-256 del contenido). browse nunca abre el archivo.
  - `res@resolution="1920x1080"` (`System.Video.FrameWidth` x `FrameHeight`).
  - `res@bitrate` en bytes/s: `System.Video.TotalBitrate` (bits/s) dividido entre 8.
  - `res@nrAudioChannels`: `System.Audio.ChannelCount`.
  - `dc:description` del item: `«<codec video> · <codec audio> <canales>»`, por ejemplo `HEVC · E-AC3 5.1` o `H.264 · AAC 2.0`. cada parte aparece solo si se conoce. canales: `1.0`, `2.0`, `5.1`, `7.1`.
  - codecs: video H.264, HEVC, VP9, AV1, MPEG-2; audio AAC, AC3, E-AC3, DTS, TrueHD, FLAC, Opus, MP3. Windows entrega el codec como FourCC o como subtipo GUID; el servidor lo traduce.
  - no se envia «10 bit» ni «HDR»: las propiedades de Windows no informan la profundidad de bits.
  - `System.Video.FrameRate` se mide, pero no se envia: el texto de arriba no tiene campo para fps.
- B. objeto `0` en BrowseMetadata: `dc:description` = `«N carpetas · M videos»`. N son las carpetas compartidas; M, los videos de la biblioteca, contados en segundo plano. mientras el conteo no termina, solo `«N carpetas»`. miles con punto (`1.284`).
- C. identidad del reproductor. el Player envia `User-Agent: VibeDLNA-Player/<versión> (<modelo>; <nombre>; id=<8 hex>)`. el servidor lo reconoce por ese formato; VLC y Kodi, por su prefijo (`VLC/3.0.18`, `Kodi/20.1`); cualquier otro cliente se nombra `Reproductor en <ip>`. el registro usa el modelo (`Hisense 65A6`) o la ip.
- D. actividad, aproximada y deducida por el servidor (no recibe la posicion del Player):
  - «viendo»: el reproductor pidio un rango de un video en los ultimos 15 s.
  - posicion = inicio del rango / tamaño del archivo x duracion medida. sin duracion, solo porcentaje.
  - `%APPDATA%\VibeDLNA\seguir-viendo.json`: una entrada por (`id` del reproductor, archivo). 7 dias, maximo 50, y se quita al pasar del 95 %. solo para reproductores con `id`.
  - `%APPDATA%\VibeDLNA\reproductores.json`: reproductores vistos, 30 dias.
  - registro: `«<cliente> abrió <carpeta> (<devueltos> de <total>)»` una vez por carpeta (primera pagina), y `«<cliente> reproduce <titulo> (<alto>p <codec>)»`, una linea por archivo y reproductor, agrupando los pedidos de 60 s.

los ids de objeto, el puerto 8210, el UUID y las miniaturas no cambian.

## 8. pendiente

- pendiente solo de verificacion: probar en TV fisica el puerto 8210, la duracion en tarjetas y la pantalla de reanudar.
- pendiente de la ampliacion 1.1.0: ventana nueva (consume la API interna de actividad) y lado Player (§A y §C).

## 9. validacion cruzada

- servidor: `dotnet test .\VibeDLNA.Tests\VibeDLNA.Tests.csproj -c Release`.
- player: `.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug` (requiere Android SDK; si el equipo no lo tiene, valida GitHub Actions).
- prueba real: servidor en Windows + player en TV en la misma red; descubrir, navegar carpeta con mas de 8 videos, ver miniaturas aparecer tras el warmup, reproducir, hacer seek, reiniciar servidor y volver a navegar.
- no afirmar compatibilidad en TV fisica sin haberla probado.
