package cl.streambox.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class ThumbnailSettingsTest {
    @Test
    public void serverModeKeepsFramePositionOnlyAsDisabledLocalSetting() {
        ThumbnailSettings settings =
                new ThumbnailSettings(ThumbnailSettings.Mode.SERVER);
        assertTrue(settings.prefersServerArtwork());
        assertEquals(50, settings.generatedPercentage());
    }

    @Test
    public void generatedModesExposeSelectedPercentage() {
        ThumbnailSettings settings =
                new ThumbnailSettings(ThumbnailSettings.Mode.GENERATED_75);
        assertFalse(settings.prefersServerArtwork());
        assertEquals(75, settings.generatedPercentage());
    }

    @Test
    public void cacheIdentityIgnoresVolatileNetworkAddress() {
        String first = ThumbnailCacheKey.name("uuid:server", "video-42", "frame-50");
        String second = ThumbnailCacheKey.name("uuid:server", "video-42", "frame-50");
        String changedMode = ThumbnailCacheKey.name("uuid:server", "video-42", "frame-75");
        assertEquals(first, second);
        assertNotEquals(first, changedMode);
    }

    @Test
    public void serverArtworkIdentityUsesAdvertisedThumbnailPath() {
        assertEquals(
                "/thumbnail/first.jpg",
                ThumbnailRepository.serverArtworkIdentity(
                        "http://192.168.1.20:43123/thumbnail/first.jpg"
                )
        );
        assertNotEquals(
                ThumbnailRepository.serverArtworkIdentity(
                        "http://192.168.1.20:43123/thumbnail/first.jpg"
                ),
                ThumbnailRepository.serverArtworkIdentity(
                        "http://192.168.1.20:43124/thumbnail/second.jpg"
                )
        );
        assertNotEquals(
                ThumbnailRepository.serverArtworkIdentity(
                        "http://192.168.1.20:43123/thumbnail/first.jpg"
                ),
                ThumbnailRepository.serverArtworkIdentity(
                        "http://192.168.1.20:43123/thumbnail/first.jpg?version=2"
                )
        );
    }
}
