package cl.streambox.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

import java.util.Locale;
import java.util.UUID;

/**
 * Identidad del Player que viaja en el encabezado User-Agent de cada pedido HTTP
 * (contrato §C): {@code VibeDLNA-Player/<versión> (<modelo>; <nombre>; id=<8 hex>)}.
 *
 * <p>El {@code id} es un UUID aleatorio creado la primera vez y guardado en el
 * dispositivo; no usa ANDROID_ID.</p>
 */
final class ClientIdentity {
    static final String PRODUCT = "VibeDLNA-Player";
    private static final String PREFS = "client_identity";
    private static final String KEY_ID = "id";
    private static final String FALLBACK_MODEL = "Android TV";

    private ClientIdentity() {
    }

    /** Arma el User-Agent. Valores vacíos o inválidos caen al respaldo indicado. */
    static String userAgent(String version, String model, String deviceName, String id) {
        String safeModel = clean(model, FALLBACK_MODEL);
        String safeName = clean(deviceName, safeModel);
        return PRODUCT + "/" + clean(version, "0")
                + " (" + safeModel + "; " + safeName + "; id=" + shortId(id) + ")";
    }

    /** Primeros 8 caracteres hexadecimales del identificador, en minúsculas. */
    static String shortId(String value) {
        if (value == null) return "";
        StringBuilder hex = new StringBuilder(8);
        for (int i = 0; i < value.length() && hex.length() < 8; i++) {
            char c = Character.toLowerCase(value.charAt(i));
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')) hex.append(c);
        }
        return hex.toString();
    }

    /**
     * Quita lo que el formato no admite (paréntesis, punto y coma, caracteres no
     * ASCII imprimibles) y colapsa espacios. Así el servidor puede separar los campos.
     */
    static String clean(String value, String fallback) {
        if (value == null) return fallback;
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == ';' || c == '(' || c == ')') {
                out.append(' ');
            } else if (c >= 0x20 && c <= 0x7E) {
                out.append(c);
            }
        }
        String collapsed = out.toString().trim().replaceAll("\\s+", " ");
        return collapsed.isEmpty() ? fallback : collapsed;
    }

    /** User-Agent real del dispositivo. Nunca lanza: si algo falla usa valores de respaldo. */
    static String forDevice(Context context) {
        String version = "0";
        try {
            PackageManager pm = context.getPackageManager();
            String name = context.getPackageName();
            version = pm.getPackageInfo(name, 0).versionName;
        } catch (Exception ignored) {
            // Sin versión legible se informa 0; el servidor igual reconoce el formato.
        }
        String model = Build.MODEL;
        String deviceName = null;
        try {
            deviceName = Settings.Global.getString(
                    context.getContentResolver(),
                    Settings.Global.DEVICE_NAME
            );
        } catch (Exception ignored) {
            // Algunos dispositivos no exponen el nombre; se usa el modelo.
        }
        return userAgent(version, model, deviceName, id(context));
    }

    /** Ocho caracteres hexadecimales estables de este dispositivo. */
    static synchronized String id(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String stored = prefs.getString(KEY_ID, null);
        if (stored != null && shortId(stored).length() == 8) return shortId(stored);
        String created = UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.ROOT);
        prefs.edit().putString(KEY_ID, created).apply();
        return shortId(created);
    }
}
