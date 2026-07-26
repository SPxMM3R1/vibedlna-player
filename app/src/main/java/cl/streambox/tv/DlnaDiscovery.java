package cl.streambox.tv;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
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
            Network network = activeNetwork();
            Map<String, URI> locations = search(timeoutMs, network);
            Map<String, DlnaServer> servers = new LinkedHashMap<>();
            for (URI location : locations.values()) {
                try {
                    DlnaServer server = describe(location, network);
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

    private Map<String, URI> search(long timeoutMs, Network network) {
        Map<String, URI> result = new LinkedHashMap<>();
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket(null);
            socket.setReuseAddress(true);
            socket.setBroadcast(true);
            socket.bind(new InetSocketAddress(0));
            if (network != null) {
                try {
                    network.bindSocket(socket);
                } catch (Exception ignored) {
                    // La ruta predeterminada sigue siendo un respaldo valido.
                }
            }
            socket.setSoTimeout(400);

            List<InetSocketAddress> destinations = searchDestinations(network);
            String[] targets = {SEARCH_TARGET, "ssdp:all"};
            for (String target : targets) {
                byte[] request = searchRequest(target);
                for (InetSocketAddress destination : destinations) {
                    DatagramPacket query = new DatagramPacket(
                            request,
                            request.length,
                            destination
                    );
                    try {
                        socket.send(query);
                        socket.send(query);
                    } catch (Exception ignored) {
                        // Probar los otros destinos aunque uno este filtrado.
                    }
                }
            }

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
                try {
                    URI uri = URI.create(location.trim());
                    if (!isHttp(uri)) continue;
                    result.put(uri.toString(), uri);
                } catch (Exception ignored) {
                    // Ignorar respuestas SSDP mal formadas.
                }
            }
        } catch (Exception ignored) {
            return result;
        } finally {
            if (socket != null) socket.close();
        }
        return result;
    }

    private DlnaServer describe(URI descriptionUri, Network network) throws Exception {
        HttpURLConnection connection = open(descriptionUri, network);
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

    private Network activeNetwork() {
        try {
            ConnectivityManager manager = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            return manager == null ? null : manager.getActiveNetwork();
        } catch (Exception ignored) {
            return null;
        }
    }

    private List<InetSocketAddress> searchDestinations(Network network)
            throws Exception {
        Map<String, InetSocketAddress> result = new LinkedHashMap<>();
        InetAddress multicast = InetAddress.getByName("239.255.255.250");
        result.put(
                multicast.getHostAddress(),
                new InetSocketAddress(multicast, 1900)
        );

        try {
            ConnectivityManager manager = (ConnectivityManager)
                    context.getSystemService(Context.CONNECTIVITY_SERVICE);
            LinkProperties properties = manager == null || network == null
                    ? null
                    : manager.getLinkProperties(network);
            if (properties != null) {
                for (LinkAddress link : properties.getLinkAddresses()) {
                    InetAddress broadcast = directedBroadcast(
                            link.getAddress(),
                            link.getPrefixLength()
                    );
                    if (broadcast == null) continue;
                    result.put(
                            broadcast.getHostAddress(),
                            new InetSocketAddress(broadcast, 1900)
                    );
                }
            }
        } catch (Exception ignored) {
            // El multicast sigue disponible aunque no se pueda calcular broadcast.
        }

        InetAddress globalBroadcast = InetAddress.getByName("255.255.255.255");
        result.put(
                globalBroadcast.getHostAddress(),
                new InetSocketAddress(globalBroadcast, 1900)
        );
        return new ArrayList<>(result.values());
    }

    static InetAddress directedBroadcast(InetAddress address, int prefixLength) {
        byte[] bytes = address.getAddress();
        if (bytes.length != 4 || prefixLength < 0 || prefixLength >= 32) return null;
        byte[] broadcast = bytes.clone();
        for (int bit = prefixLength; bit < 32; bit++) {
            int index = bit / 8;
            int mask = 1 << (7 - (bit % 8));
            broadcast[index] = (byte) (broadcast[index] | mask);
        }
        try {
            return InetAddress.getByAddress(broadcast);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static byte[] searchRequest(String target) {
        return (
                "M-SEARCH * HTTP/1.1\r\n"
                        + "HOST: 239.255.255.250:1900\r\n"
                        + "MAN: \"ssdp:discover\"\r\n"
                        + "MX: 2\r\n"
                        + "ST: " + target + "\r\n"
                        + "USER-AGENT: Android/1.0 UPnP/1.1 VibeDLNA/0.3\r\n"
                        + "\r\n"
        ).getBytes(StandardCharsets.UTF_8);
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

    private static HttpURLConnection open(URI uri, Network network) throws Exception {
        HttpURLConnection connection;
        try {
            connection = (HttpURLConnection) (
                    network == null
                            ? uri.toURL().openConnection()
                            : network.openConnection(uri.toURL())
            );
        } catch (Exception ignored) {
            connection = (HttpURLConnection) uri.toURL().openConnection();
        }
        connection.setConnectTimeout(5_000);
        connection.setReadTimeout(8_000);
        connection.setRequestProperty("User-Agent", "VibeDLNA/0.3 UPnP/1.1");
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
