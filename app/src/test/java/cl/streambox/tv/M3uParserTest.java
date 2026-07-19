package cl.streambox.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.net.URI;
import java.util.List;

public class M3uParserTest {
    @Test
    public void parsesNamesLogosGroupsAndRelativeUrls() {
        String playlist = "#EXTM3U\n" +
                "#EXTINF:-1 tvg-logo=\"logos/norte.png\" group-title=\"Deportes\",Norte Deportes HD\n" +
                "streams/norte.m3u8\n" +
                "#EXTINF:-1 tvg-name=\"Noticias 24\",\n" +
                "https://media.example.org/news.m3u8\n";

        List<Channel> channels = M3uParser.parse(playlist, URI.create("https://example.org/lists/tv.m3u"));

        assertEquals(2, channels.size());
        assertEquals("Norte Deportes HD", channels.get(0).getName());
        assertEquals("Deportes", channels.get(0).getGroup());
        assertEquals("https://example.org/lists/streams/norte.m3u8", channels.get(0).getStreamUri().toString());
        assertEquals("https://example.org/lists/logos/norte.png", channels.get(0).getLogoUri().toString());
        assertEquals("Noticias 24", channels.get(1).getName());
    }

    @Test
    public void ignoresCommentsAndMalformedUrls() {
        String playlist = "#EXTM3U\n# comentario\n://mal\nhttps://example.org/ok.m3u8\n";
        List<Channel> channels = M3uParser.parse(playlist, URI.create("https://example.org/list.m3u"));
        assertEquals(1, channels.size());
        assertEquals("Canal 1", channels.get(0).getName());
        assertNull(channels.get(0).getLogoUri());
    }
}
