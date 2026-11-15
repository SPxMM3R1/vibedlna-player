package cl.streambox.tv;

import android.content.Context;
import android.content.SharedPreferences;

final class LibraryPreferences {
    private static final String PREFS = "dlna_settings";
    private static final String KEY_SERVER_UDN = "server_udn";
    private static final String KEY_SERVER_NAME = "server_name";
    private static final String KEY_CONTAINER_ID = "container_id";
    private static final String KEY_CONTAINER_NAME = "container_name";
    private static final String KEY_PARENT_ID = "parent_id";
    private static final String KEY_PARENT_NAME = "parent_name";

    private final SharedPreferences preferences;

    LibraryPreferences(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    String getServerUdn() {
        return preferences.getString(KEY_SERVER_UDN, "");
    }

    String getServerName() {
        return preferences.getString(KEY_SERVER_NAME, "");
    }

    String getContainerId() {
        return preferences.getString(KEY_CONTAINER_ID, "0");
    }

    String getContainerName() {
        return preferences.getString(KEY_CONTAINER_NAME, "Inicio");
    }

    void selectServer(DlnaServer server) {
        preferences.edit()
                .putString(KEY_SERVER_UDN, server.getUdn())
                .putString(KEY_SERVER_NAME, server.getFriendlyName())
                .putString(KEY_CONTAINER_ID, "0")
                .putString(KEY_CONTAINER_NAME, "Inicio")
                .apply();
    }

    /** Carpeta abierta y el nivel donde está, para restaurar la columna al volver a entrar. */
    void selectContainer(String id, String name, String parentId, String parentName) {
        preferences.edit()
                .putString(KEY_CONTAINER_ID, id)
                .putString(KEY_CONTAINER_NAME, name)
                .putString(KEY_PARENT_ID, parentId == null ? "0" : parentId)
                .putString(KEY_PARENT_NAME, parentName == null ? "Inicio" : parentName)
                .apply();
    }

    String getParentId() {
        return preferences.getString(KEY_PARENT_ID, "0");
    }

    String getParentName() {
        return preferences.getString(KEY_PARENT_NAME, "Inicio");
    }
}
