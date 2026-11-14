package cl.streambox.tv;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Pantalla Opciones (diseño v1): pestañas de texto «Biblioteca · Reproducción ·
 * Servidores · Acerca de» y, debajo, filas con su valor tras « · » y una
 * aclaración. OK en una fila cambia su valor o ejecuta su acción.
 */
final class OptionsScreen {
    interface Host {
        void onSortChanged();

        void onRetentionChanged();

        void onClearThumbnails();

        void onOpenServersRequested();

        void onCheckUpdatesRequested();
    }

    static final String[] TABS = {"Biblioteca", "Reproducción", "Servidores", "Acerca de"};

    private final Context context;
    private final PlayerSettings settings;
    private final Host host;
    private final String version;
    private final LinearLayout tabRow;
    private final LinearLayout rows;
    private final List<TextView> tabViews = new ArrayList<>();
    private int tab;
    private long thumbnailCount;
    private long thumbnailBytes;

    OptionsScreen(Context context, PlayerSettings settings, Host host, String version,
                  LinearLayout tabRow, LinearLayout rows) {
        this.context = context;
        this.settings = settings;
        this.host = host;
        this.version = version;
        this.tabRow = tabRow;
        this.rows = rows;
        buildTabs();
    }

    /** Muestra la pestaña indicada y devuelve la primera fila para dar el foco. */
    View show(int index) {
        tab = Math.max(0, Math.min(TABS.length - 1, index));
        updateTabs();
        rebuildRows();
        return rows.getChildCount() > 0 ? rows.getChildAt(0) : tabViews.get(tab);
    }

    int selectedTab() {
        return tab;
    }

    void setThumbnailStats(long count, long bytes) {
        thumbnailCount = count;
        thumbnailBytes = bytes;
        if (tab == 0) rebuildRows();
    }

    View firstTab() {
        return tabViews.get(0);
    }

    private void buildTabs() {
        for (int i = 0; i < TABS.length; i++) {
            final int index = i;
            TextView label = TvUi.text(context, TABS[i], 14f, TvUi.INK_2, false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            params.rightMargin = TvUi.dp(context, 30);
            label.setLayoutParams(params);
            label.setFocusable(true);
            label.setOnClickListener(view -> {
                show(index);
            });
            label.setOnFocusChangeListener((view, focused) -> {
                TvUi.setFocusedText(label, focused || index == tab);
                if (focused && index != tab) {
                    label.setTextColor(TvUi.INK_2);
                }
            });
            tabViews.add(label);
            tabRow.addView(label);
        }
        tabRow.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
    }

    private void updateTabs() {
        for (int i = 0; i < tabViews.size(); i++) {
            TextView label = tabViews.get(i);
            boolean selected = i == tab;
            label.setTextColor(selected ? TvUi.WHITE : TvUi.INK_2);
            label.setTypeface(null, selected
                    ? android.graphics.Typeface.BOLD
                    : android.graphics.Typeface.NORMAL);
        }
    }

    private void rebuildRows() {
        rows.removeAllViews();
        for (Row row : rowsFor(tab)) addRow(row);
    }

    private List<Row> rowsFor(int index) {
        List<Row> list = new ArrayList<>();
        switch (index) {
            case 0:
                list.add(Row.cycle(
                        "Orden de las carpetas",
                        sortLabel(settings.sortOrder()),
                        "Por nombre · Más recientes · Tamaño",
                        () -> {
                            settings.setSortOrder((settings.sortOrder() + 1) % 3);
                            host.onSortChanged();
                        }
                ));
                list.add(Row.cycle(
                        "Mostrar archivos que la TV no puede abrir",
                        settings.showUnplayable() ? "Sí" : "No",
                        "Oculta los códecs que esta TV no decodifica (p. ej. DTS sin soporte)",
                        () -> settings.setShowUnplayable(!settings.showUnplayable()),
                        true
                ));
                list.add(Row.info(
                        "Miniaturas",
                        "Las del servidor",
                        "Solo se ven las que el servidor anuncia; esta app no las genera"
                ));
                list.add(Row.action(
                        "Borrar miniaturas guardadas",
                        thumbnailCount + (thumbnailCount == 1 ? " imagen" : " imágenes")
                                + " · " + megabytes(thumbnailBytes),
                        "Se vuelven a recibir del servidor al abrir las carpetas",
                        host::onClearThumbnails
                ));
                break;
            case 1:
                list.add(Row.cycle(
                        "Recordar dónde quedaste",
                        retentionLabel(settings.resumeRetention()),
                        "1 hora · 1 día · Siempre",
                        () -> {
                            settings.setResumeRetention((settings.resumeRetention() + 1) % 3);
                            host.onRetentionChanged();
                        }
                ));
                break;
            case 2:
                list.add(Row.cycle(
                        "Abrir en",
                        settings.openAtLastServer() ? "El último servidor" : "Elegir cada vez",
                        "El último servidor · Elegir cada vez",
                        () -> settings.setOpenAtLastServer(!settings.openAtLastServer())
                ));
                list.add(Row.action(
                        "Cambiar de servidor",
                        "",
                        "Busca otra vez los servidores de la red",
                        host::onOpenServersRequested
                ));
                break;
            default:
                list.add(Row.info(
                        "VibeDLNA Player " + version,
                        "",
                        "Busca actualizaciones en GitHub al abrir"
                ));
                list.add(Row.action(
                        "Buscar actualización",
                        "",
                        "Consulta la última versión publicada",
                        host::onCheckUpdatesRequested
                ));
                break;
        }
        return list;
    }

    private void addRow(Row row) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setClipChildren(false);
        box.setFocusable(row.focusable);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = TvUi.dp(context, 26);
        box.setLayoutParams(params);

        String head = row.value.isEmpty() ? row.label : row.label + " · " + row.value;
        TextView label = TvUi.text(context, head, 17f, TvUi.INK_2, false);
        label.setSingleLine(false);
        box.addView(label);

        TextView hint = TvUi.text(context, row.hint, 13f, TvUi.INK_3, false);
        hint.setSingleLine(false);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        hintParams.topMargin = TvUi.dp(context, 5);
        box.addView(hint, hintParams);

        if (row.focusable) {
            box.setOnClickListener(view -> {
                row.action.run();
                rebuildRows();
            });
            box.setOnFocusChangeListener((view, focused) -> {
                TvUi.setFocusedText(label, focused);
                label.setTextColor(focused ? TvUi.WHITE : TvUi.INK_2);
            });
        }
        rows.addView(box);
    }

    static String sortLabel(int order) {
        switch (order) {
            case PlayerSettings.SORT_RECENT:
                return "Más recientes";
            case PlayerSettings.SORT_SIZE:
                return "Tamaño";
            default:
                return "Por nombre";
        }
    }

    static String retentionLabel(int option) {
        switch (option) {
            case PlayerSettings.RETENTION_DAY:
                return "1 día";
            case PlayerSettings.RETENTION_FOREVER:
                return "Siempre";
            default:
                return "1 hora";
        }
    }

    private static String megabytes(long bytes) {
        if (bytes <= 0L) return "0 KB";
        if (bytes < 1024L * 1024L) return Math.max(1L, bytes / 1024L) + " KB";
        return DlnaMediaFormat.size(bytes);
    }

    private static final class Row {
        final String label;
        final String value;
        final String hint;
        final Runnable action;
        final boolean focusable;

        private Row(String label, String value, String hint, Runnable action, boolean focusable) {
            this.label = label;
            this.value = value;
            this.hint = hint;
            this.action = action;
            this.focusable = focusable;
        }

        static Row cycle(String label, String value, String hint, Runnable action) {
            return new Row(label, value, hint, action, true);
        }

        static Row cycle(String label, String value, String hint, Runnable action, boolean ignored) {
            return new Row(label, value, hint, action, true);
        }

        static Row action(String label, String value, String hint, Runnable action) {
            return new Row(label, value, hint, action, true);
        }

        static Row info(String label, String value, String hint) {
            return new Row(label, value, hint, () -> {
            }, false);
        }
    }
}
