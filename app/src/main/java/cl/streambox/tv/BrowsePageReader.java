package cl.streambox.tv;

/**
 * Lectura de una carpeta por páginas (contrato §2). Pura: no conoce jUPnP.
 *
 * <p>Cada página se pide con {@link #PAGE_SIZE} elementos. Si falla, se vuelve a pedir una sola
 * vez. Si vuelve a fallar, sus elementos se piden uno a uno y los que no lleguen dejan la lectura
 * incompleta. Las páginas siguientes se leen igual: lo que llega se muestra y el aviso lo dice.
 * Si nada llega en absoluto, la lectura falla. Si dos ventanas seguidas no dan nada, se corta
 * para no esperar al servidor caído página por página.
 */
final class BrowsePageReader {
    static final long PAGE_SIZE = 8L;
    static final long MAX_ITEMS = 2_000L;
    /** Ventanas seguidas sin ningún elemento que cortan la lectura. */
    private static final int DEAD_WINDOWS_LIMIT = 2;

    private BrowsePageReader() {
    }

    /** Lo que trajo una ventana: el contenido, cuántos elementos son y el total de la carpeta. */
    static final class Window<T> {
        final T content;
        final long returned;
        final long total;

        Window(T content, long returned, long total) {
            this.content = content;
            this.returned = returned;
            this.total = total;
        }
    }

    /** Pide la ventana {@code [start, start + count)}. Lanza una excepción si el servidor falla. */
    interface Source<T> {
        Window<T> request(long start, long count) throws Exception;
    }

    /** Guarda el contenido de una ventana y devuelve cuántos elementos quedan guardados. */
    interface Sink<T> {
        long accept(T content);
    }

    /** {@code complete} es falso si algún elemento no se pudo leer. */
    static final class Outcome {
        final boolean complete;
        final long saved;

        Outcome(boolean complete, long saved) {
            this.complete = complete;
            this.saved = saved;
        }
    }

    static <T> Outcome read(Source<T> source, Sink<T> sink) throws Exception {
        long start = 0L;
        long total = -1L;
        long saved = 0L;
        boolean complete = true;
        boolean readAny = false;
        int deadWindows = 0;
        Exception failure = null;

        while ((total < 0L || start < total) && saved < MAX_ITEMS) {
            Window<T> page = null;
            for (int attempt = 0; attempt < 2 && page == null; attempt++) {
                try {
                    page = source.request(start, PAGE_SIZE);
                } catch (Exception error) {
                    failure = error;
                }
            }
            if (page != null) {
                saved = sink.accept(page.content);
                readAny = true;
                deadWindows = 0;
                if (page.total > 0L) total = page.total;
                long next = advance(start, page.returned);
                if (next == start) break;
                start = next;
                continue;
            }

            // La página falló dos veces: se piden sus elementos uno a uno.
            long end = recoveryEnd(start, total);
            boolean rescued = false;
            for (long index = start; index < end && saved < MAX_ITEMS; index++) {
                Window<T> item = null;
                try {
                    item = source.request(index, 1L);
                } catch (Exception error) {
                    failure = error;
                }
                if (item == null) {
                    complete = false;
                    continue;
                }
                saved = sink.accept(item.content);
                readAny = true;
                rescued = true;
                if (item.total > 0L) total = item.total;
            }
            if (!rescued) {
                complete = false;
                deadWindows++;
                if (deadWindows >= DEAD_WINDOWS_LIMIT) break;
            } else {
                deadWindows = 0;
            }
            start = end;
        }

        if (!readAny) {
            throw failure != null
                    ? failure
                    : new java.io.IOException("No se pudo leer la carpeta del servidor DLNA.");
        }
        return new Outcome(complete, saved);
    }

    /** Siguiente inicio tras leer {@code returned} elementos desde {@code start}. */
    static long advance(long start, long returned) {
        if (returned <= 0L) return start;
        long next = start + returned;
        return next <= start ? start : next;
    }

    /** Fin de la ventana que se rescata elemento a elemento: una página, sin pasar del total. */
    static long recoveryEnd(long start, long total) {
        long candidate = start + PAGE_SIZE;
        if (candidate < start) candidate = Long.MAX_VALUE;
        return total > 0L && total < Long.MAX_VALUE
                ? Math.min(total, candidate)
                : candidate;
    }
}
