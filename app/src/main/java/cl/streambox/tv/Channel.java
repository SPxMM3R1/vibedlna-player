package cl.streambox.tv;

import java.net.URI;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Channel {
    private final String name;
    private final URI streamUri;
    private final URI logoUri;
    private final String group;
    private final Map<String, String> attributes;

    public Channel(String name, URI streamUri, URI logoUri, String group, Map<String, String> attributes) {
        this.name = name;
        this.streamUri = streamUri;
        this.logoUri = logoUri;
        this.group = group;
        this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    public String getName() { return name; }
    public URI getStreamUri() { return streamUri; }
    public URI getLogoUri() { return logoUri; }
    public String getGroup() { return group; }
    public Map<String, String> getAttributes() { return attributes; }
}
