package cl.streambox.tv;

final class ThumbnailSettings {
    enum Mode {
        SERVER,
        GENERATED_25,
        GENERATED_50,
        GENERATED_75
    }

    private final Mode mode;

    ThumbnailSettings(Mode mode) {
        this.mode = mode == null ? Mode.SERVER : mode;
    }

    Mode mode() {
        return mode;
    }

    boolean prefersServerArtwork() {
        return mode == Mode.SERVER;
    }

    int generatedPercentage() {
        switch (mode) {
            case GENERATED_25:
                return 25;
            case GENERATED_75:
                return 75;
            case GENERATED_50:
            case SERVER:
            default:
                return 50;
        }
    }

    String cacheVariant() {
        return prefersServerArtwork() ? "server-fallback-50" : "frame-" + generatedPercentage();
    }
}
