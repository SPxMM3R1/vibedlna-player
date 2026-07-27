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
import java.util.List;
import java.util.Locale;

final class DlnaContentRepository {
    static final class BrowseResult {
        final List<VideoItem> entries;
        final String parentId;

        BrowseResult(List<VideoItem> entries, String parentId) {
            this.entries = entries;
            this.parentId = parentId;
        }
    }

    private static final int PAGE_SIZE = 200;
    private static final int MAX_ITEMS = 2_000;

    private final DlnaDiscovery discovery;

    DlnaContentRepository(DlnaDiscovery discovery) {
        this.discovery = discovery;
    }

    BrowseResult browse(DlnaServer server, String objectId) throws Exception {
        List<VideoItem> result = new ArrayList<>();
        long start = 0L;
        long total = Long.MAX_VALUE;
        do {
            Page page = browsePage(
                    server.getContentDirectoryService(),
                    objectId,
                    BrowseFlag.DIRECT_CHILDREN,
                    start,
                    PAGE_SIZE
            );
            addEntries(page.content, result, server.getUdn());
            if (page.returned <= 0L) break;
            start += page.returned;
            if (page.total > 0L) total = page.total;
        } while (start < total && result.size() < MAX_ITEMS);

        String parentId = "0".equals(objectId)
                ? null
                : metadataParentId(server, objectId);
        return new BrowseResult(result, parentId);
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

        discovery.execute(callback);
        if (page.failure != null) throw page.failure;
        if (page.content == null) page.content = new DIDLContent();
        return page;
    }

    private static void addEntries(
            DIDLContent content,
            List<VideoItem> result,
            String serverUdn
    ) {
        for (Container container : content.getContainers()) {
            if (result.size() >= MAX_ITEMS) return;
            String id = container.getId();
            if (id == null || id.isBlank()) continue;
            String title = container.getTitle();
            result.add(VideoItem.container(
                    serverUdn,
                    id,
                    container.getParentID(),
                    title == null || title.isBlank() ? "Carpeta" : title
            ));
        }

        for (Item item : content.getItems()) {
            if (result.size() >= MAX_ITEMS) return;
            Resource resource = videoResource(item);
            if (resource == null) continue;
            String id = item.getId();
            String title = item.getTitle();
            result.add(VideoItem.video(
                    serverUdn,
                    id == null || id.isBlank() ? resource.uri.toString() : id,
                    item.getParentID(),
                    title == null || title.isBlank() ? "Video" : title,
                    resource.uri,
                    artworkUri(item, resource.uri),
                    resource.mimeType,
                    resource.durationMs
            ));
        }
    }

    private static Uri artworkUri(Item item, Uri mediaUri) {
        try {
            URI value = item.getFirstPropertyValue(
                    DIDLObject.Property.UPNP.ALBUM_ART_URI.class
            );
            if (value == null) return null;
            URI resolved = value.isAbsolute()
                    ? value
                    : URI.create(mediaUri.toString()).resolve(value);
            return Uri.parse(resolved.toString());
        } catch (Exception ignored) {
            return null;
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
                    durationMillis(resource.getDuration())
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

        Resource(Uri uri, String mimeType, long durationMs) {
            this.uri = uri;
            this.mimeType = mimeType;
            this.durationMs = durationMs;
        }
    }
}
