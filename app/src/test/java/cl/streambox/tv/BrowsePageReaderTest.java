package cl.streambox.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BrowsePageReaderTest {
    private static final int ALWAYS = Integer.MAX_VALUE;

    /** Carpeta simulada con elementos 0..total-1; cada ventana puede fallar, una o más veces. */
    private static final class FakeFolder implements BrowsePageReader.Source<List<Integer>> {
        private final long total;
        private final Map<String, Integer> failuresLeft = new HashMap<>();
        final List<String> calls = new ArrayList<>();
        boolean allFail;

        FakeFolder(long total) {
            this.total = total;
        }

        /** La ventana {@code start:count} falla {@code times} veces; {@link #ALWAYS} es siempre. */
        FakeFolder fail(long start, long count, int times) {
            failuresLeft.put(start + ":" + count, times);
            return this;
        }

        @Override
        public BrowsePageReader.Window<List<Integer>> request(long start, long count) throws IOException {
            String key = start + ":" + count;
            calls.add(key);
            Integer left = failuresLeft.get(key);
            if (allFail || (left != null && left > 0)) {
                if (left != null && left != ALWAYS) failuresLeft.put(key, left - 1);
                throw new IOException("fallo " + key);
            }
            List<Integer> items = new ArrayList<>();
            for (long i = start; i < Math.min(total, start + count); i++) items.add((int) i);
            return new BrowsePageReader.Window<>(items, items.size(), total);
        }
    }

    private static final class Collector implements BrowsePageReader.Sink<List<Integer>> {
        final List<Integer> saved = new ArrayList<>();

        @Override
        public long accept(List<Integer> content) {
            saved.addAll(content);
            return saved.size();
        }
    }

    @Test
    public void readsWholeFolderWithoutRetriesWhenServerAnswers() throws Exception {
        FakeFolder folder = new FakeFolder(20);
        Collector collected = new Collector();

        BrowsePageReader.Outcome outcome = BrowsePageReader.read(folder, collected);

        assertTrue(outcome.complete);
        assertEquals(20, collected.saved.size());
        assertEquals(Arrays.asList("0:8", "8:8", "16:8"), folder.calls);
    }

    @Test
    public void retriesAFailedPageOnceAndKeepsItsItems() throws Exception {
        FakeFolder folder = new FakeFolder(20).fail(8, 8, 1);
        Collector collected = new Collector();

        BrowsePageReader.Outcome outcome = BrowsePageReader.read(folder, collected);

        assertTrue(outcome.complete);
        assertEquals(20, collected.saved.size());
        assertEquals(Arrays.asList("0:8", "8:8", "8:8", "16:8"), folder.calls);
    }

    @Test
    public void pageThatFailsTwiceIsRescuedItemByItem() throws Exception {
        FakeFolder folder = new FakeFolder(20).fail(8, 8, ALWAYS);
        Collector collected = new Collector();

        BrowsePageReader.Outcome outcome = BrowsePageReader.read(folder, collected);

        assertTrue(outcome.complete);
        assertEquals(20, collected.saved.size());
        assertEquals(Arrays.asList(
                "0:8", "8:8", "8:8",
                "8:1", "9:1", "10:1", "11:1", "12:1", "13:1", "14:1", "15:1",
                "16:8"
        ), folder.calls);
    }

    @Test
    public void unreadableItemLeavesFolderIncompleteButLaterPagesStillArrive() throws Exception {
        FakeFolder folder = new FakeFolder(20).fail(8, 8, ALWAYS).fail(10, 1, ALWAYS);
        Collector collected = new Collector();

        BrowsePageReader.Outcome outcome = BrowsePageReader.read(folder, collected);

        assertFalse(outcome.complete);
        assertEquals(19, collected.saved.size());
        assertFalse(collected.saved.contains(10));
        assertTrue(collected.saved.containsAll(Arrays.asList(16, 17, 18, 19)));
    }

    @Test
    public void failedFirstPageIsRescuedWhenItsItemsAnswer() throws Exception {
        FakeFolder folder = new FakeFolder(20).fail(0, 8, ALWAYS);
        Collector collected = new Collector();

        BrowsePageReader.Outcome outcome = BrowsePageReader.read(folder, collected);

        assertTrue(outcome.complete);
        assertEquals(20, collected.saved.size());
    }

    @Test(expected = IOException.class)
    public void nothingArrivingAtAllFailsTheRead() throws Exception {
        FakeFolder folder = new FakeFolder(20);
        folder.allFail = true;

        BrowsePageReader.read(folder, new Collector());
    }

    @Test
    public void deadServerStopsAfterTwoEmptyWindowsAndKeepsWhatArrived() throws Exception {
        FakeFolder folder = new FakeFolder(1000);
        for (long start = 8; start < 24; start += 8) {
            folder.fail(start, 8, ALWAYS);
            for (long item = start; item < start + 8; item++) folder.fail(item, 1, ALWAYS);
        }
        Collector collected = new Collector();

        BrowsePageReader.Outcome outcome = BrowsePageReader.read(folder, collected);

        assertFalse(outcome.complete);
        assertEquals(8, collected.saved.size());
        assertFalse(folder.calls.contains("24:8"));
    }

    @Test
    public void emptyFolderIsCompleteAndEmpty() throws Exception {
        FakeFolder folder = new FakeFolder(0);
        Collector collected = new Collector();

        BrowsePageReader.Outcome outcome = BrowsePageReader.read(folder, collected);

        assertTrue(outcome.complete);
        assertTrue(collected.saved.isEmpty());
        assertEquals(Arrays.asList("0:8"), folder.calls);
    }

    @Test
    public void stopsAtTheItemLimit() throws Exception {
        FakeFolder folder = new FakeFolder(3_000);
        Collector collected = new Collector();

        BrowsePageReader.Outcome outcome = BrowsePageReader.read(folder, collected);

        assertTrue(outcome.complete);
        assertEquals(BrowsePageReader.MAX_ITEMS, collected.saved.size());
    }
}
