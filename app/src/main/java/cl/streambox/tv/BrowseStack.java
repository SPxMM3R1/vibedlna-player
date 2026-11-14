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
}
