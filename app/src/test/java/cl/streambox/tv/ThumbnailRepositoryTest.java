package cl.streambox.tv;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import org.junit.Test;

public final class ThumbnailRepositoryTest {
    @Test
    public void acceptsOnlyAnnouncedRemoteArtwork() {
        assertTrue(ThumbnailRepository.isRemoteArtwork(
                Uri.parse("http://192.168.1.20:43123/thumbnail/video.jpg")
        ));
        assertTrue(ThumbnailRepository.isRemoteArtwork(
                Uri.parse("https://192.168.1.20:43123/images/video.webp")
        ));
        assertFalse(ThumbnailRepository.isRemoteArtwork(
                Uri.parse("file:///thumbnail/video.jpg")
        ));
        assertFalse(ThumbnailRepository.isRemoteArtwork(null));
        assertFalse(ThumbnailRepository.isRemoteArtwork(
                Uri.parse("http://192.168.1.20:43123/thumbnail/request")
        ));
        assertFalse(ThumbnailRepository.isRemoteArtwork(
                Uri.parse("http://192.168.1.20:43123/thumbnail/request/video.jpg")
        ));
    }

    @Test
    public void cacheIdentityIncludesTheExactAnnouncedArtworkUrl() {
        VideoItem first = video("http://192.168.1.20:43123/thumbnail/first.jpg");
        VideoItem second = video("http://192.168.1.20:43123/thumbnail/second.jpg");

        assertNotEquals(
                ThumbnailRepository.cacheName(first),
                ThumbnailRepository.cacheName(second)
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

    private static VideoItem video(String artworkUrl) {
        return VideoItem.video(
                "uuid:server",
                "video-1",
                "0",
                "video.mp4",
                Uri.parse("http://192.168.1.20:43123/media/video-1/video.mp4"),
                Uri.parse(artworkUrl),
                "video/mp4",
                0L
        );
    }
}
