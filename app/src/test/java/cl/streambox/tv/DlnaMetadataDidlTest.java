package cl.streambox.tv;

import static org.junit.Assert.assertEquals;

import org.jupnp.support.contentdirectory.DIDLParser;
import org.jupnp.support.model.DIDLContent;
import org.jupnp.support.model.item.Item;
import org.junit.Test;

public final class DlnaMetadataDidlTest {
    private static final String DIDL =
            "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\""
                    + " xmlns:dc=\"http://purl.org/dc/elements/1.1/\""
                    + " xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">"
                    + "<item id=\"F:abc\" parentID=\"D:xyz\" restricted=\"1\">"
                    + "<dc:title>Dune</dc:title>"
                    + "<dc:description>HEVC · E-AC3 5.1</dc:description>"
                    + "<dc:date>2024-03-01</dc:date>"
                    + "<upnp:class>object.item.videoItem</upnp:class>"
                    + "<res protocolInfo=\"http-get:*:video/mp4:DLNA.ORG_OP=01\""
                    + " size=\"24270000000\" bitrate=\"2275000\""
                    + " resolution=\"3840x2160\" nrAudioChannels=\"6\""
                    + " duration=\"2:46:00.000\">http://192.168.1.20:8210/media/a/Dune.mp4</res>"
                    + "</item>"
                    + "<item id=\"F:def\" parentID=\"D:xyz\" restricted=\"1\">"
                    + "<dc:title>Sin datos</dc:title>"
                    + "<upnp:class>object.item.videoItem</upnp:class>"
                    + "<res protocolInfo=\"http-get:*:video/mp4:DLNA.ORG_OP=01\">"
                    + "http://192.168.1.20:8210/media/b/Sin.mp4</res>"
                    + "</item>"
                    + "</DIDL-Lite>";

    @Test
    public void readsTechnicalAttributesFromDidl() throws Exception {
        DIDLContent content = new DIDLParser().parse(DIDL);
        Item item = content.getItems().get(0);
        MediaInfo media = DlnaContentRepository.readMedia(item.getResources().get(0), item);

        assertEquals("3840x2160", media.resolution);
        assertEquals(2160, media.height());
        assertEquals("4K", DlnaMediaFormat.qualityLabel(media.height()));
        assertEquals(2_275_000L, media.bitrateBytesPerSecond);
        assertEquals(6L, media.audioChannels);
        assertEquals(24_270_000_000L, media.sizeBytes);
        assertEquals("HEVC · E-AC3 5.1", media.codecs);
        assertEquals("2024", DlnaMediaFormat.year(media.date));
    }

    @Test
    public void missingAttributesStayEmpty() throws Exception {
        DIDLContent content = new DIDLParser().parse(DIDL);
        Item item = content.getItems().get(1);
        MediaInfo media = DlnaContentRepository.readMedia(item.getResources().get(0), item);

        assertEquals("", media.resolution);
        assertEquals(0, media.height());
        assertEquals(0L, media.bitrateBytesPerSecond);
        assertEquals("", media.codecs);
        assertEquals("", media.date);
    }
}
