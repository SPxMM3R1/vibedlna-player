package cl.streambox.tv;

import org.jupnp.model.meta.Service;

final class DlnaServer {
    /** Fabricante que anuncia el servidor VibeDLNA (contrato §7 B). */
    static final String VIBEDLNA_MANUFACTURER = "VibeDLNA";

    private final String udn;
    private final String friendlyName;
    private final Service<?, ?> contentDirectoryService;
    private final String manufacturer;
    private final String modelName;
    private volatile String summary = "";

    DlnaServer(
            String udn,
            String friendlyName,
            Service<?, ?> contentDirectoryService
    ) {
        this(udn, friendlyName, contentDirectoryService, "", "");
    }

    DlnaServer(
            String udn,
            String friendlyName,
            Service<?, ?> contentDirectoryService,
            String manufacturer,
            String modelName
    ) {
        this.udn = udn;
        this.friendlyName = friendlyName;
        this.contentDirectoryService = contentDirectoryService;
        this.manufacturer = manufacturer == null ? "" : manufacturer;
        this.modelName = modelName == null ? "" : modelName;
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

    String getManufacturer() {
        return manufacturer;
    }

    String getModelName() {
        return modelName;
    }

    boolean isVibeDlna() {
        return VIBEDLNA_MANUFACTURER.equalsIgnoreCase(manufacturer.trim());
    }

    /** «N carpetas · M videos» de la raíz, o vacío mientras no llega. */
    String getSummary() {
        return summary;
    }

    void setSummary(String value) {
        summary = value == null ? "" : value;
    }

    /** Línea bajo el nombre en la pantalla de Servidores. */
    String subtitle() {
        if (isVibeDlna()) {
            return summary.isEmpty() ? "VibeDLNA" : "VibeDLNA · " + summary;
        }
        String brand = manufacturer.trim();
        String model = modelName.trim();
        if (brand.isEmpty()) return model;
        if (model.isEmpty()) return brand;
        return brand + " · " + model;
    }
}
