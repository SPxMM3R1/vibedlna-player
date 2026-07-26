package cl.streambox.tv;

import java.net.URI;

final class DlnaServer {
    private final String udn;
    private final String friendlyName;
    private final URI descriptionUri;
    private final URI controlUri;
    private final String serviceType;

    DlnaServer(
            String udn,
            String friendlyName,
            URI descriptionUri,
            URI controlUri,
            String serviceType
    ) {
        this.udn = udn;
        this.friendlyName = friendlyName;
        this.descriptionUri = descriptionUri;
        this.controlUri = controlUri;
        this.serviceType = serviceType;
    }

    String getUdn() {
        return udn;
    }

    String getFriendlyName() {
        return friendlyName;
    }

    URI getDescriptionUri() {
        return descriptionUri;
    }

    URI getControlUri() {
        return controlUri;
    }

    String getServiceType() {
        return serviceType;
    }
}
