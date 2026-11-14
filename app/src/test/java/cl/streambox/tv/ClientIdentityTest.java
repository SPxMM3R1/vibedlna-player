package cl.streambox.tv;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ClientIdentityTest {
    @Test
    public void buildsTheContractUserAgent() {
        assertEquals(
                "VibeDLNA-Player/0.4.0 (Hisense 65A6; Living; id=4f1c9a92)",
                ClientIdentity.userAgent("0.4.0", "Hisense 65A6", "Living",
                        "4f1c9a92-0000-4000-8000-000000000000")
        );
    }

    @Test
    public void fallsBackToModelWhenNameIsMissing() {
        assertEquals(
                "VibeDLNA-Player/0.4.0 (Hisense 65A6; Hisense 65A6; id=abcdef01)",
                ClientIdentity.userAgent("0.4.0", "Hisense 65A6", "  ", "abcdef01")
        );
    }

    @Test
    public void removesSeparatorsFromNames() {
        assertEquals("Sala 2 TV", ClientIdentity.clean("Sala (2); TV", "x"));
    }

    @Test
    public void dropsNonAsciiCharactersFromNames() {
        assertEquals("Sala TV", ClientIdentity.clean("Sala áñ TV", "x"));
    }

    @Test
    public void keepsOnlyEightHexCharacters() {
        assertEquals("4f1c9a92", ClientIdentity.shortId("4F1C-9A92-ffff"));
        assertEquals("", ClientIdentity.shortId(null));
    }
}
