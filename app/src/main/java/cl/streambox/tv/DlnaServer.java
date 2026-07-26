package cl.streambox.tv;

import org.jupnp.model.meta.Service;

final class DlnaServer {
    private final String udn;
    private final String friendlyName;
    private final Service<?, ?> contentDirectoryService;

    DlnaServer(
            String udn,
            String friendlyName,
            Service<?, ?> contentDirectoryService
    ) {
        this.udn = udn;
        this.friendlyName = friendlyName;
        this.contentDirectoryService = contentDirectoryService;
    }

    String getUdn() {
        return udn;
    }

    String getFriendlyName() {
        return friendlyName;
    }

    Service<?, ?> getContentDirectoryService() {
        return contentDirectoryService;
    }
}
