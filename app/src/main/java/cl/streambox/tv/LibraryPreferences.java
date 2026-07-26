package cl.streambox.tv;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

final class LibraryPreferences {
    private static final String PREFS = "library_settings";
    private static final String KEY_FOLDER_URI = "folder_uri";
    private static final String KEY_FOLDER_NAME = "folder_name";
    private static final String KEY_SORT = "sort";
    static final String SORT_NAME = "name";
    static final String SORT_DATE = "date";

    private final SharedPreferences preferences;

    LibraryPreferences(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    Uri getFolderUri() {
        String value = preferences.getString(KEY_FOLDER_URI, "");
        return value == null || value.isBlank() ? null : Uri.parse(value);
    }

    String getFolderName() {
        return preferences.getString(KEY_FOLDER_NAME, "Videos");
    }

    void setFolder(Uri uri, String name) {
        preferences.edit()
                .putString(KEY_FOLDER_URI, uri.toString())
                .putString(KEY_FOLDER_NAME, name == null || name.isBlank() ? "Videos" : name)
                .apply();
    }

    String getSortMode() {
        return preferences.getString(KEY_SORT, SORT_NAME);
    }

    void toggleSortMode() {
        preferences.edit()
                .putString(KEY_SORT, SORT_NAME.equals(getSortMode()) ? SORT_DATE : SORT_NAME)
                .apply();
    }
}
