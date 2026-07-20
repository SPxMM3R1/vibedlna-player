package cl.streambox.tv;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Playlist {
    private final List<Channel> channels;
    private final URI epgUri;

    public Playlist(List<Channel> channels, URI epgUri) {
        this.channels = Collections.unmodifiableList(new ArrayList<>(channels));
        this.epgUri = epgUri;
    }

    public List<Channel> getChannels() { return channels; }
    public URI getEpgUri() { return epgUri; }
}
