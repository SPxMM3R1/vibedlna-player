package cl.streambox.tv;

import android.net.Uri;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
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
    private static final int MAX_RESPONSE_BYTES = 8_000_000;

    BrowseResult browse(DlnaServer server, String objectId) throws Exception {
        List<VideoItem> result = new ArrayList<>();
        int start = 0;
        int total = Integer.MAX_VALUE;
        do {
            String response = browseRequest(
                    server,
                    objectId,
                    "BrowseDirectChildren",
                    start,
                    PAGE_SIZE
            );
            Document envelope = DlnaXml.parse(response);
            String didl = DlnaXml.firstText(envelope, "Result");
            int returned = integer(DlnaXml.firstText(envelope, "NumberReturned"));
            int reportedTotal = integer(DlnaXml.firstText(envelope, "TotalMatches"));
            if (reportedTotal > 0) total = reportedTotal;
            if (!didl.isBlank()) parseDidl(didl, result);
            if (returned <= 0) break;
            start += returned;
        } while (start < total && result.size() < MAX_ITEMS);

        String parentId = "0".equals(objectId)
                ? null
                : metadataParentId(server, objectId);
        return new BrowseResult(result, parentId);
    }

    private String metadataParentId(DlnaServer server, String objectId) {
        try {
            String response = browseRequest(
                    server,
                    objectId,
                    "BrowseMetadata",
                    0,
                    0
            );
            Document envelope = DlnaXml.parse(response);
            String didl = DlnaXml.firstText(envelope, "Result");
            if (didl.isBlank()) return "0";
            Document metadata = DlnaXml.parse(didl);
            Element container = DlnaXml.firstDescendant(metadata, "container");
            if (container == null) container = DlnaXml.firstDescendant(metadata, "item");
            if (container == null) return "0";
            String parentId = container.getAttribute("parentID");
            return parentId.isBlank() || "-1".equals(parentId) ? "0" : parentId;
        } catch (Exception ignored) {
            return "0";
        }
    }

    private String browseRequest(
            DlnaServer server,
            String objectId,
            String flag,
            int start,
            int requestedCount
    ) throws Exception {
        String serviceType = server.getServiceType();
        String body = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\""
                + " s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
                + "<s:Body><u:Browse xmlns:u=\"" + xml(serviceType) + "\">"
                + "<ObjectID>" + xml(objectId) + "</ObjectID>"
                + "<BrowseFlag>" + flag + "</BrowseFlag>"
                + "<Filter>*</Filter>"
                + "<StartingIndex>" + start + "</StartingIndex>"
                + "<RequestedCount>" + requestedCount + "</RequestedCount>"
                + "<SortCriteria></SortCriteria>"
                + "</u:Browse></s:Body></s:Envelope>";

        HttpURLConnection connection = (HttpURLConnection)
                server.getControlUri().toURL().openConnection();
        connection.setConnectTimeout(7_000);
        connection.setReadTimeout(20_000);
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
        connection.setRequestProperty("SOAPACTION", "\"" + serviceType + "#Browse\"");
        connection.setRequestProperty("User-Agent", "VibeDLNA/0.2 UPnP/1.1");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(bytes);
        }

        int status = connection.getResponseCode();
        InputStream input = status >= 200 && status < 300
                ? connection.getInputStream()
                : connection.getErrorStream();
        try {
            String response = input == null ? "" : readLimited(input, MAX_RESPONSE_BYTES);
            if (status < 200 || status >= 300) {
                throw new IOException("El servidor DLNA respondió " + status + ".");
            }
            return response;
        } finally {
            connection.disconnect();
        }
    }

    private static void parseDidl(String xml, List<VideoItem> result) throws Exception {
        Document document = DlnaXml.parse(xml);
        for (Element container : DlnaXml.descendants(document, "container")) {
            if (result.size() >= MAX_ITEMS) return;
            String id = container.getAttribute("id");
            if (id.isBlank()) continue;
            String parentId = container.getAttribute("parentID");
            String title = DlnaXml.firstText(container, "title");
            result.add(VideoItem.container(
                    id,
                    parentId,
                    title.isBlank() ? "Carpeta" : title
            ));
        }

        for (Element item : DlnaXml.descendants(document, "item")) {
            if (result.size() >= MAX_ITEMS) return;
            String itemClass = DlnaXml.firstText(item, "class");
            Resource resource = videoResource(item, itemClass);
            if (resource == null) continue;
            String id = item.getAttribute("id");
            String parentId = item.getAttribute("parentID");
            String title = DlnaXml.firstText(item, "title");
            result.add(VideoItem.video(
                    id.isBlank() ? resource.uri.toString() : id,
                    parentId,
                    title.isBlank() ? "Video" : title,
                    resource.uri,
                    resource.mimeType,
                    resource.durationMs
            ));
        }
    }

    private static Resource videoResource(Element item, String itemClass) {
        for (Element res : DlnaXml.descendants(item, "res")) {
            String uriValue = res.getTextContent().trim();
            if (uriValue.isBlank()) continue;
            String protocolInfo = res.getAttribute("protocolInfo");
            String mimeType = mimeType(protocolInfo);
            boolean video = mimeType.startsWith("video/")
                    || itemClass.toLowerCase(Locale.ROOT).contains("videoitem");
            if (!video) continue;
            try {
                Uri uri = Uri.parse(uriValue);
                String scheme = uri.getScheme();
                if (!"http".equalsIgnoreCase(scheme)
                        && !"https".equalsIgnoreCase(scheme)) continue;
                return new Resource(
                        uri,
                        mimeType,
                        duration(res.getAttribute("duration"))
                );
            } catch (Exception ignored) {
                // Continuar con el siguiente recurso anunciado.
            }
        }
        return null;
    }

    private static String mimeType(String protocolInfo) {
        if (protocolInfo == null) return "";
        String[] parts = protocolInfo.split(":", 4);
        return parts.length >= 3 ? parts[2].trim() : "";
    }

    private static long duration(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            String normalized = value;
            int decimal = normalized.indexOf('.');
            if (decimal >= 0) normalized = normalized.substring(0, decimal);
            String[] parts = normalized.split(":");
            if (parts.length != 3) return 0;
            long seconds = Long.parseLong(parts[0]) * 3_600L
                    + Long.parseLong(parts[1]) * 60L
                    + Long.parseLong(parts[2]);
            return seconds * 1_000L;
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static int integer(String value) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private static String xml(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static String readLimited(InputStream input, int limit) throws IOException {
        StringBuilder result = new StringBuilder();
        int total = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                total += read;
                if (total > limit) throw new IOException("Respuesta DLNA demasiado grande.");
                result.append(buffer, 0, read);
            }
        }
        return result.toString();
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
