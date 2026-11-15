package cl.streambox.tv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Niveles de la columna de carpetas. La base es siempre la raíz del servidor
 * («0»); cada OK en una carpeta con subcarpetas agrega un nivel y «‹» lo quita.
 * Pura: se prueba en la JVM.
 */
final class BrowseStack {
    static final String ROOT_ID = "0";
    static final String ROOT_NAME = "Inicio";

    static final class Level {
        final String id;
        final String name;

        Level(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private final List<Level> levels = new ArrayList<>();

    BrowseStack() {
        reset();
    }

    /** Vuelve a la raíz. */
    void reset() {
        levels.clear();
        levels.add(new Level(ROOT_ID, ROOT_NAME));
    }

    /** Agrega un nivel; repetir el nivel actual no hace nada. */
    void push(String id, String name) {
        if (id == null || id.isBlank()) return;
        if (id.equals(top().id)) return;
        levels.add(new Level(id, name == null || name.isBlank() ? "Carpeta" : name));
    }

    /** Quita el nivel actual. En la raíz no hace nada y devuelve null. */
    Level pop() {
        if (levels.size() <= 1) return null;
        return levels.remove(levels.size() - 1);
    }

    /** Nivel cuyas carpetas se muestran. */
    Level top() {
        return levels.get(levels.size() - 1);
    }

    /** Nivel inmediatamente superior, o null si estamos en la raíz. */
    Level parent() {
        return levels.size() < 2 ? null : levels.get(levels.size() - 2);
    }

    boolean isRoot() {
        return levels.size() == 1;
    }

    int depth() {
        return levels.size();
    }

    /** Copia de la ruta desde la raíz. */
    List<Level> path() {
        return Collections.unmodifiableList(new ArrayList<>(levels));
    }

    private static final char FIELD_SEPARATOR = '\u001F';
    private static final char LEVEL_SEPARATOR = '\u001E';

    /**
     * Texto para guardar una ruta: «id» y «nombre» de cada nivel, separados por caracteres de
     * control que no aparecen en los nombres de las carpetas.
     */
    static String encode(List<Level> path) {
        StringBuilder text = new StringBuilder();
        for (Level level : path) {
            if (text.length() > 0) text.append(LEVEL_SEPARATOR);
            text.append(clean(level.id)).append(FIELD_SEPARATOR).append(clean(level.name));
        }
        return text.toString();
    }

    /** Niveles de un texto guardado por {@link #encode}; lo que no se entiende se omite. */
    static List<Level> decode(String text) {
        List<Level> path = new ArrayList<>();
        if (text == null || text.isEmpty()) return path;
        for (String record : text.split(String.valueOf(LEVEL_SEPARATOR))) {
            int split = record.indexOf(FIELD_SEPARATOR);
            if (split <= 0) continue;
            String id = record.substring(0, split);
            if (id.isBlank()) continue;
            path.add(new Level(id, record.substring(split + 1)));
        }
        return path;
    }

    private static String clean(String value) {
        if (value == null) return "";
        return value.replace(FIELD_SEPARATOR, ' ').replace(LEVEL_SEPARATOR, ' ');
    }
}
