package cl.streambox.tv;

import android.net.Uri;

import org.jupnp.controlpoint.ActionCallback;
import org.jupnp.model.action.ActionInvocation;
import org.jupnp.model.message.UpnpResponse;
import org.jupnp.model.meta.Service;
import org.jupnp.support.contentdirectory.callback.Browse;
import org.jupnp.support.model.BrowseFlag;
import org.jupnp.support.model.DIDLContent;
import org.jupnp.support.model.DIDLObject;
import org.jupnp.support.model.Protocol;
import org.jupnp.support.model.ProtocolInfo;
import org.jupnp.support.model.Res;
import org.jupnp.support.model.container.Container;
import org.jupnp.support.model.item.Item;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class DlnaContentRepository {
    static final class BrowseResult {
        final List<VideoItem> entries;
        final String parentId;
        /** Falso si alguna página o elemento no llegó tras reintentar: la lista está incompleta. */
        final boolean incomplete;

        BrowseResult(List<VideoItem> entries, String parentId, boolean incomplete) {
            this.entries = entries;
            this.parentId = parentId;
            this.incomplete = incomplete;
        }
    }

    /*
     * Keep Browse windows bounded so a slow or malformed server response does
     * not block the rest of a folder. Thumbnail loading happens only after
     * DIDL has been parsed and never participates in Browse. La paginación
     * (reintentos y rescate por elemento) vive en BrowsePageReader.
     */
    private static final long BROWSE_TIMEOUT_MS = 15_000L;

    private final DlnaDiscovery discovery;

    DlnaContentRepository(DlnaDiscovery discovery) {
        this.discovery = discovery;
    }

    BrowseResult browse(DlnaServer server, String objectId) throws Exception {
        List<VideoItem> result = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        BrowsePageReader.Outcome outcome = BrowsePageReader.<DIDLContent>read(
                (start, count) -> {
                    Page page = browsePage(
                            server.getContentDirectoryService(),
                            objectId,
                            BrowseFlag.DIRECT_CHILDREN,
                            start,
                            count
                    );
                    return new BrowsePageReader.Window<>(page.content, page.returned, page.total);
                },
                content -> {
                    addEntries(content, result, server.getUdn(), seenIds);
                    return result.size();
                }
        );

        String parentId = "0".equals(objectId)
                ? null
                : metadataParentId(server, objectId);
        return new BrowseResult(result, parentId, !outcome.complete);
    }

    private String metadataParentId(DlnaServer server, String objectId) {
        try {
            Page page = browsePage(
                    server.getContentDirectoryService(),
                    objectId,
                    BrowseFlag.METADATA,
                    0L,
                    1L
            );
            DIDLObject object = firstObject(page.content);
            if (object == null) return "0";
            String parentId = object.getParentID();
            return parentId == null || parentId.isBlank() || "-1".equals(parentId)
                    ? "0"
                    : parentId;
        } catch (Exception ignored) {
            return "0";
        }
    }

    private Page browsePage(
            Service<?, ?> service,
            String objectId,
            BrowseFlag flag,
            long start,
            long requestedCount
    ) throws Exception {
        Page page = new Page();
        ActionCallback callback = new Browse(
                service,
                objectId,
                flag,
                Browse.CAPS_WILDCARD,
                start,
                requestedCount
        ) {
            @Override
            public boolean receivedRaw(
                    ActionInvocation<?> invocation,
                    org.jupnp.support.model.BrowseResult result
            ) {
                page.returned = result.getCountLong();
                page.total = result.getTotalMatchesLong();
                return true;
            }

            @Override
            public void received(
                    ActionInvocation<?> invocation,
                    DIDLContent content
            ) {
                page.content = content;
            }

            @Override
            public void updateStatus(Status status) {
                // La interfaz administra su propio indicador de carga.
            }

            @Override
            public void failure(
                    ActionInvocation invocation,
                    UpnpResponse response,
                    String defaultMessage
            ) {
                page.failure = new IOException(defaultMessage);
            }
        };

        Future<?> request = discovery.execute(callback);
        try {
            request.get(BROWSE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            request.cancel(true);
            Thread.currentThread().interrupt();
            throw new IOException("La lectura de la carpeta DLNA fue interrumpida.", interrupted);
        } catch (TimeoutException timeout) {
            request.cancel(true);
            throw new IOException("Tiempo de espera agotado al leer la carpeta del servidor DLNA.", timeout);
        } catch (CancellationException cancelled) {
            throw new IOException("La lectura de la carpeta del servidor DLNA fue cancelada.", cancelled);
        } catch (ExecutionException execution) {
            Throwable cause = execution.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw new IOException("Falló la lectura de la carpeta del servidor DLNA.", cause);
        }
        if (page.failure != null) throw page.failure;
        if (page.content == null) {
            throw new IOException("El servidor DLNA no devolvió contenido para Browse.");
        }
        return page;
    }

    static long advanceBrowseStart(long start, long returned) {
        return BrowsePageReader.advance(start, returned);
    }

    static long recoveryEnd(long start, long total) {
        return BrowsePageReader.recoveryEnd(start, total);
    }

    private static void addEntries(
            DIDLContent content,
            List<VideoItem> result,
            String serverUdn,
            Set<String> seenIds
    ) {
        for (Container container : content.getContainers()) {
            if (result.size() >= BrowsePageReader.MAX_ITEMS) return;
            String id = container.getId();
            if (id == null || id.isBlank()) continue;
            if (!seenIds.add("container:" + id)) continue;
            String title = container.getTitle();
            result.add(VideoItem.container(
                    serverUdn,
                    id,
                    container.getParentID(),
                    title == null || title.isBlank() ? "Carpeta" : title
            ));
        }

        for (Item item : content.getItems()) {
            if (result.size() >= BrowsePageReader.MAX_ITEMS) return;
            Resource resource = videoResource(item);
            if (resource == null) continue;
            String id = item.getId();
            String stableId = id == null || id.isBlank() ? resource.uri.toString() : id;
            if (!seenIds.add("item:" + stableId)) continue;
            String title = item.getTitle();
            result.add(VideoItem.video(
                    serverUdn,
                    stableId,
                    item.getParentID(),
                    title == null || title.isBlank() ? "Video" : title,
                    resource.uri,
                    artworkUri(item, resource.uri),
                    resource.mimeType,
                    resource.durationMs,
                    resource.media
            ));
        }
    }

    private static Uri artworkUri(Item item, Uri mediaUri) {
        if (mediaUri == null) return null;
        try {
            URI resolved = announcedArtworkUri(
                    item,
                    URI.create(mediaUri.toString())
            );
            return resolved == null ? null : Uri.parse(resolved.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    static URI announcedArtworkUri(Item item, URI mediaUri) {
        try {
            URI value = item.getFirstPropertyValue(
                    DIDLObject.Property.UPNP.ALBUM_ART_URI.class
            );
            return value == null ? null : resolveArtworkUri(value, mediaUri);
        } catch (Exception ignored) {
            return null;
        }
    }

    static URI resolveArtworkUri(URI artworkUri, URI mediaUri) {
        if (artworkUri == null || mediaUri == null) return null;
        try {
            URI resolved = artworkUri;
            if (!artworkUri.isAbsolute()) {
                URI media = URI.create(mediaUri.toString());
                URI serverOrigin = new URI(
                        media.getScheme(),
                        media.getAuthority(),
                        "/",
                        null,
                        null
                );
                resolved = serverOrigin.resolve(artworkUri).normalize();
            }
            String scheme = resolved.getScheme();
            if (!"http".equalsIgnoreCase(scheme)
                    && !"https".equalsIgnoreCase(scheme)) {
                return null;
            }
            return resolved.toString().isBlank() ? null : resolved;
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Datos técnicos de un recurso (contrato §7 A): resolución, tasa en bytes/s,
     * canales y tamaño del atributo {@code res}, y {@code dc:description} y
     * {@code dc:date} del item. Lo que falte queda vacío.
     */
    static MediaInfo readMedia(Res resource, DIDLObject item) {
        String resolution = resource.getResolution();
        Long bitrate = resource.getBitrate();
        Long channels = resource.getNrAudioChannels();
        Long size = resource.getSize();
        String description = item.getFirstPropertyValue(
                DIDLObject.Property.DC.DESCRIPTION.class
        );
        String date = item.getFirstPropertyValue(DIDLObject.Property.DC.DATE.class);
        return new MediaInfo(
                resolution == null ? "" : resolution.trim(),
                bitrate == null ? 0L : bitrate,
                channels == null ? 0L : channels,
                size == null ? 0L : size,
                date == null ? "" : date.trim(),
                description == null ? "" : description.trim()
        );
    }

    /**
     * «N carpetas · M videos» de la raíz (contrato §7 B), leído de la
     * dc:description de BrowseMetadata sobre el objeto 0. Vacío si no llega.
     */
    String rootSummary(DlnaServer server) {
        try {
            Page page = browsePage(
                    server.getContentDirectoryService(),
                    "0",
                    BrowseFlag.METADATA,
                    0L,
                    1L
            );
            DIDLObject object = firstObject(page.content);
            if (object == null) return "";
            String description = object.getFirstPropertyValue(
                    DIDLObject.Property.DC.DESCRIPTION.class
            );
            return description == null ? "" : description.trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static DIDLObject firstObject(DIDLContent content) {
        if (!content.getContainers().isEmpty()) return content.getContainers().get(0);
        if (!content.getItems().isEmpty()) return content.getItems().get(0);
        return null;
    }

    private static Resource videoResource(Item item) {
        String itemClass = item.getClazz() == null
                ? ""
                : item.getClazz().getValue();
        Resource best = null;
        long bestScore = Long.MIN_VALUE;
        for (Res resource : item.getResources()) {
            String value = resource.getValue();
            if (value == null || value.isBlank()) continue;
            ProtocolInfo protocolInfo = resource.getProtocolInfo();
            String mimeType = protocolInfo == null
                    ? ""
                    : protocolInfo.getContentFormat();
            boolean video = mimeType.toLowerCase(Locale.ROOT).startsWith("video/")
                    || itemClass.toLowerCase(Locale.ROOT).contains("videoitem");
            if (!video) continue;
            Uri uri = Uri.parse(value);
            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme)
                    && !"https".equalsIgnoreCase(scheme)) continue;

            long score = resourceScore(resource, protocolInfo);
            if (best != null && score <= bestScore) continue;
            best = new Resource(
                    uri,
                    mimeType,
                    durationMillis(resource.getDuration()),
                    readMedia(resource, item)
            );
            bestScore = score;
        }
        return best;
    }

    private static long resourceScore(Res resource, ProtocolInfo protocolInfo) {
        long score = 0L;
        if (protocolInfo != null) {
            if (Protocol.HTTP_GET.equals(protocolInfo.getProtocol())) {
                score += 1_000_000_000L;
            }
            String extra = protocolInfo.getAdditionalInfo();
            if (extra != null) {
                String normalized = extra.toUpperCase(Locale.ROOT);
                if (normalized.contains("DLNA.ORG_CI=0")) score += 100_000_000L;
                if (normalized.contains("DLNA.ORG_CI=1")) score -= 100_000_000L;
            }
        }
        score += Math.min(resolutionPixels(resource.getResolution()), 50_000_000L);
        Long bitrate = resource.getBitrate();
        if (bitrate != null && bitrate > 0L) {
            score += Math.min(bitrate / 1_000L, 10_000_000L);
        }
        return score;
    }

    private static long resolutionPixels(String resolution) {
        if (resolution == null || resolution.isBlank()) return 0L;
        try {
            String[] parts = resolution.toLowerCase(Locale.ROOT).split("x", 2);
            if (parts.length != 2) return 0L;
            return Long.parseLong(parts[0]) * Long.parseLong(parts[1]);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    static long durationMillis(String value) {
        if (value == null || value.isBlank()) return 0L;
        try {
            String normalized = value;
            int decimal = normalized.indexOf('.');
            if (decimal >= 0) normalized = normalized.substring(0, decimal);
            String[] parts = normalized.split(":");
            if (parts.length != 3) return 0L;
            long seconds = Long.parseLong(parts[0]) * 3_600L
                    + Long.parseLong(parts[1]) * 60L
                    + Long.parseLong(parts[2]);
            return seconds * 1_000L;
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private static final class Page {
        DIDLContent content;
        long returned;
        long total;
        IOException failure;
    }

    private static final class Resource {
        final Uri uri;
        final String mimeType;
        final long durationMs;
        final MediaInfo media;

        Resource(Uri uri, String mimeType, long durationMs, MediaInfo media) {
            this.uri = uri;
            this.mimeType = mimeType;
            this.durationMs = durationMs;
            this.media = media;
        }
    }
}
