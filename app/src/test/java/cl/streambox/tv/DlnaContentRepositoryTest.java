package cl.streambox.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.net.URI;

import org.junit.Test;

import org.jupnp.support.contentdirectory.DIDLParser;
import org.jupnp.support.model.DIDLContent;
import org.jupnp.support.model.DIDLObject;

public final class DlnaContentRepositoryTest {
    @Test
    public void parsesDlnaDuration() {
        assertEquals(
                3_723_000L,
                DlnaContentRepository.durationMillis("01:02:03.500")
        );
    }

    @Test
    public void rejectsInvalidDuration() {
        assertEquals(0L, DlnaContentRepository.durationMillis(""));
        assertEquals(0L, DlnaContentRepository.durationMillis("12:34"));
        assertEquals(0L, DlnaContentRepository.durationMillis("desconocida"));
    }

    @Test
    public void advancesOnlyWhenBrowseReturnsItems() {
        assertEquals(8L, DlnaContentRepository.advanceBrowseStart(0L, 8L));
        assertEquals(8L, DlnaContentRepository.advanceBrowseStart(8L, 0L));
        assertEquals(16L, DlnaContentRepository.advanceBrowseStart(8L, 8L));
    }

    @Test
    public void limitsRecoveryToKnownTotal() {
        assertEquals(16L, DlnaContentRepository.recoveryEnd(8L, 16L));
        assertEquals(16L, DlnaContentRepository.recoveryEnd(8L, 20L));
        assertEquals(16L, DlnaContentRepository.recoveryEnd(8L, Long.MAX_VALUE));
    }

    @Test
    public void parsesVibeDlnaBrowseDidlWithServerArtwork() throws Exception {
        String didl = "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\""
                + " xmlns:dc=\"http://purl.org/dc/elements/1.1/\""
                + " xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">"
                + "<item id=\"R:video\" parentID=\"R:0\" restricted=\"1\">"
                + "<dc:title>video.mp4</dc:title>"
                + "<upnp:class>object.item.videoItem</upnp:class>"
                + "<res protocolInfo=\"http-get:*:video/mp4:DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01700000000000000000000000000000\">"
                + "http://192.168.1.20:43123/media/R%3Avideo/video.mp4</res>"
                + "<upnp:albumArtURI>http://192.168.1.20:43123/thumbnail/abc.jpg</upnp:albumArtURI>"
                + "</item></DIDL-Lite>";

        DIDLContent content = new DIDLParser().parse(didl);
        DIDLObject item = content.getItems().get(0);
        assertEquals("R:video", item.getId());
        assertEquals(
                URI.create("http://192.168.1.20:43123/thumbnail/abc.jpg"),
                item.getFirstPropertyValue(DIDLObject.Property.UPNP.ALBUM_ART_URI.class)
        );
    }

    @Test
    public void acceptsServerThumbnailEndpoint() {
        URI artwork = DlnaContentRepository.resolveArtworkUri(
                URI.create("http://192.168.1.20:43123/thumbnail/abc.jpg"),
                URI.create("http://192.168.1.20:43123/media/video/file.mp4")
        );
        assertEquals(
                "http://192.168.1.20:43123/thumbnail/abc.jpg",
                artwork.toString()
        );
    }

    @Test
    public void resolvesRelativeThumbnailAgainstServerOrigin() {
        URI artwork = DlnaContentRepository.resolveArtworkUri(
                URI.create("thumbnail/abc.jpg"),
                URI.create("http://192.168.1.20:43123/media/video/file.mp4")
        );
        assertEquals(
                "http://192.168.1.20:43123/thumbnail/abc.jpg",
                artwork.toString()
        );
    }

    @Test
    public void rejectsNonHttpArtwork() {
        assertNull(DlnaContentRepository.resolveArtworkUri(
                URI.create("file:///thumbnail/abc.jpg"),
                URI.create("http://192.168.1.20:43123/media/video/file.mp4")
        ));
    }
}
