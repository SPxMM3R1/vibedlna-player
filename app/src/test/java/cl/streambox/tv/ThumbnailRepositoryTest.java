package cl.streambox.tv;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ThumbnailRepositoryTest {
    @Test
    public void acceptsOnlyAnnouncedRemoteArtwork() {
        assertTrue(ThumbnailRepository.isRemoteArtworkUrl(
                "http://192.168.1.20:43123/thumbnail/video.jpg"
        ));
        assertTrue(ThumbnailRepository.isRemoteArtworkUrl(
                "https://192.168.1.20:43123/images/video.webp"
        ));
        assertFalse(ThumbnailRepository.isRemoteArtworkUrl(
                "file:///thumbnail/video.jpg"
        ));
        assertFalse(ThumbnailRepository.isRemoteArtworkUrl(null));
    }

    @Test
    public void cacheIdentityIncludesTheExactAnnouncedArtworkUrl() {
        String first = "http://192.168.1.20:43123/thumbnail/first.jpg";
        String second = "http://192.168.1.20:43123/thumbnail/second.jpg";

        assertNotEquals(
                ThumbnailRepository.cacheNameForArtwork("uuid:server", "video-1", first),
                ThumbnailRepository.cacheNameForArtwork("uuid:server", "video-1", second)
        );
    }

    @Test
    public void sourcePolicyHasNoLocalProducer() {
        for (ThumbnailRepository.Source source : ThumbnailRepository.Source.values()) {
            assertTrue(
                    source == ThumbnailRepository.Source.SERVER
                            || source == ThumbnailRepository.Source.NONE
            );
        }
    }
}
