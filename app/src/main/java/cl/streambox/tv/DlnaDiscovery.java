package cl.streambox.tv;

import android.content.Context;
import android.net.wifi.WifiManager;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class DlnaDiscovery {
    private static final String SEARCH_TARGET =
            "urn:schemas-upnp-org:device:MediaServer:1";
    private static final int MAX_DESCRIPTION_BYTES = 2_000_000;

    private final Context context;

    DlnaDiscovery(Context context) {
        this.context = context.getApplicationContext();
    }

    List<DlnaServer> discover(long timeoutMs) {
        WifiManager.MulticastLock lock = acquireMulticastLock();
        try {
            Map<String, URI> locations = search(timeoutMs);
            Map<String, DlnaServer> servers = new LinkedHashMap<>();
            for (URI location : locations.values()) {
                try {
                    DlnaServer server = describe(location);
                    if (server != null) servers.put(server.getUdn(), server);
                } catch (Exception ignored) {
                    // Un dispositivo defectuoso no debe ocultar a los demás.
                }
            }
            List<DlnaServer> result = new ArrayList<>(servers.values());
            Collections.sort(result, new Comparator<DlnaServer>() {
                @Override
                public int compare(DlnaServer left, DlnaServer right) {
                    return left.getFriendlyName().compareToIgnoreCase(
                            right.getFriendlyName()
                    );
                }
            });
            return result;
        } finally {
            if (lock != null && lock.isHeld()) lock.release();
        }
    }

    private Map<String, URI> search(long timeoutMs) {
        Map<String, URI> result = new LinkedHashMap<>();
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(0));
            socket.setSoTimeout(350);
            byte[] request = (
                    "M-SEARCH * HTTP/1.1\r\n"
                            + "HOST: 239.255.255.250:1900\r\n"
                            + "MAN: \"ssdp:discover\"\r\n"
                            + "MX: 2\r\n"
                            + "ST: " + SEARCH_TARGET + "\r\n"
                            + "USER-AGENT: Android/1.0 UPnP/1.1 VibeDLNA/0.2\r\n"
                            + "\r\n"
            ).getBytes(StandardCharsets.UTF_8);
            InetAddress group = InetAddress.getByName("239.255.255.250");
            DatagramPacket query = new DatagramPacket(request, request.length, group, 1900);
            socket.send(query);
            socket.send(query);

            long deadline = System.currentTimeMillis() + timeoutMs;
            byte[] buffer = new byte[16_384];
            while (System.currentTimeMillis() < deadline) {
                DatagramPacket response = new DatagramPacket(buffer, buffer.length);
                try {
                    socket.receive(response);
                } catch (SocketTimeoutException ignored) {
                    continue;
                }
                String message = new String(
                        response.getData(),
                        response.getOffset(),
                        response.getLength(),
                        StandardCharsets.UTF_8
                );
                Map<String, String> headers = headers(message);
                String location = headers.get("location");
                if (location == null || location.isBlank()) continue;
                URI uri = URI.create(location.trim());
                if (!isHttp(uri)) continue;
                result.put(uri.toString(), uri);
            }
        } catch (Exception ignored) {
            return result;
        } finally {
            if (socket != null) socket.close();
        }
        return result;
    }

    private DlnaServer describe(URI descriptionUri) throws Exception {
        HttpURLConnection connection = open(descriptionUri);
        String xml;
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) return null;
            xml = readLimited(connection.getInputStream(), MAX_DESCRIPTION_BYTES);
        } finally {
            connection.disconnect();
        }

        Document document = DlnaXml.parse(xml);
        URI baseUri = descriptionUri;
        String urlBase = DlnaXml.firstText(document, "URLBase");
        if (!urlBase.isBlank()) {
            try {
                URI announcedBase = URI.create(urlBase);
                if (isHttp(announcedBase)) baseUri = announcedBase;
            } catch (Exception ignored) {
                // La URL del documento sigue siendo una base valida.
            }
        }
        for (Element device : DlnaXml.descendants(document, "device")) {
            String type = DlnaXml.firstText(device, "deviceType");
            if (!type.contains(":device:MediaServer:")) continue;
            String friendlyName = DlnaXml.firstText(device, "friendlyName");
            String udn = DlnaXml.firstText(device, "UDN");
            if (udn.isBlank()) udn = descriptionUri.toString();

            for (Element service : DlnaXml.descendants(device, "service")) {
                String serviceType = DlnaXml.firstText(service, "serviceType");
                if (!serviceType.contains(":service:ContentDirectory:")) continue;
                String controlUrl = DlnaXml.firstText(service, "controlURL");
                if (controlUrl.isBlank()) continue;
                URI controlUri = baseUri.resolve(controlUrl);
                if (!isHttp(controlUri)) continue;
                return new DlnaServer(
                        udn,
                        friendlyName.isBlank() ? "Servidor DLNA" : friendlyName,
                        descriptionUri,
                        controlUri,
                        serviceType
                );
            }
        }
        return null;
    }

    private WifiManager.MulticastLock acquireMulticastLock() {
        try {
            WifiManager manager = (WifiManager) context.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (manager == null) return null;
            WifiManager.MulticastLock lock =
                    manager.createMulticastLock("VibeDLNA-discovery");
            lock.setReferenceCounted(false);
            lock.acquire();
            return lock;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static Map<String, String> headers(String message) {
        Map<String, String> result = new HashMap<>();
        String[] lines = message.split("\\r?\\n");
        for (String line : lines) {
            int separator = line.indexOf(':');
            if (separator <= 0) continue;
            result.put(
                    line.substring(0, separator).trim().toLowerCase(Locale.ROOT),
                    line.substring(separator + 1).trim()
            );
        }
        return result;
    }

    private static HttpURLConnection open(URI uri) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(8_000);
        connection.setRequestProperty("User-Agent", "VibeDLNA/0.2 UPnP/1.1");
        return connection;
    }

    private static String readLimited(InputStream input, int limit) throws IOException {
        StringBuilder result = new StringBuilder();
        int total = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                total += read;
                if (total > limit) throw new IOException("Descripción DLNA demasiado grande.");
                result.append(buffer, 0, read);
            }
        }
        return result.toString();
    }

    private static boolean isHttp(URI uri) {
        return "http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme());
    }
}
