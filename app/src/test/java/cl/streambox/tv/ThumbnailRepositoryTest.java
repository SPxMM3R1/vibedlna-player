package cl.streambox.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

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
    public void cacheIdentitySurvivesServerAddressChanges() {
        String beforeRestart = "http://192.168.1.20:43123/thumbnail/abc123.jpg";
        String afterRestart = "http://192.168.1.31:8210/thumbnail/abc123.jpg";

        assertEquals(
                ThumbnailRepository.cacheNameForArtwork("uuid:server", "F:video", beforeRestart),
                ThumbnailRepository.cacheNameForArtwork("uuid:server", "F:video", afterRestart)
        );
        assertNotEquals(
                ThumbnailRepository.cacheNameForArtwork("uuid:server", "F:video", afterRestart),
                ThumbnailRepository.cacheNameForArtwork("uuid:other", "F:video", afterRestart)
        );
        assertEquals(
                "/thumbnail/abc.jpg?v=2",
                ThumbnailRepository.artworkIdentity("http://host:1/thumbnail/abc.jpg?v=2")
        );
    }

    @Test
    public void pruneKeepsMostRecentlyUsedFiles() throws IOException {
        File directory = Files.createTempDirectory("vibe-thumbs").toFile();
        try {
            for (int index = 0; index < 5; index++) {
                File file = new File(directory, "thumb-" + index + ".jpg");
                assertTrue(file.createNewFile());
                assertTrue(file.setLastModified(1_000_000L + index * 10_000L));
            }

            assertEquals(0, ThumbnailRepository.pruneOldest(directory.listFiles(), 5));
            assertEquals(2, ThumbnailRepository.pruneOldest(directory.listFiles(), 3));

            assertFalse(new File(directory, "thumb-0.jpg").exists());
            assertFalse(new File(directory, "thumb-1.jpg").exists());
            assertTrue(new File(directory, "thumb-4.jpg").exists());
            assertEquals(3, directory.listFiles().length);
        } finally {
            File[] remaining = directory.listFiles();
            if (remaining != null) {
                for (File file : remaining) file.delete();
            }
            directory.delete();
        }
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
