package cl.streambox.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.net.URI;

import org.junit.Test;

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
