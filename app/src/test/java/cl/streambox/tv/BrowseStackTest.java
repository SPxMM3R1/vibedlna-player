package cl.streambox.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

public final class BrowseStackTest {
    @Test
    public void startsAtRootWithoutParent() {
        BrowseStack stack = new BrowseStack();
        assertTrue(stack.isRoot());
        assertEquals("0", stack.top().id);
        assertNull(stack.parent());
        assertNull(stack.pop());
    }

    @Test
    public void pushAndPopKeepParentName() {
        BrowseStack stack = new BrowseStack();
        stack.push("F:series", "Series");
        assertFalse(stack.isRoot());
        assertEquals("Inicio", stack.parent().name);
        stack.push("D:temporada", "Temporada 1");
        assertEquals("Series", stack.parent().name);
        assertEquals(3, stack.depth());
        assertEquals("Temporada 1", stack.pop().name);
        assertEquals("Series", stack.top().name);
    }

    @Test
    public void ignoresRepeatedLevelAndBlankIds() {
        BrowseStack stack = new BrowseStack();
        stack.push("D:x", "X");
        stack.push("D:x", "X");
        stack.push("", "vacio");
        assertEquals(2, stack.depth());
    }

    @Test
    public void resetReturnsToRoot() {
        BrowseStack stack = new BrowseStack();
        stack.push("D:x", "X");
        stack.reset();
        assertTrue(stack.isRoot());
    }

    @Test
    public void parentIsAlwaysTheLevelBelowTheTop() {
        BrowseStack stack = new BrowseStack();
        stack.push("D:a", "Series");
        stack.push("D:b", "Temporada 1");
        stack.push("D:c", "Capitulos");
        assertEquals("Temporada 1", stack.parent().name);
        stack.pop();
        assertEquals("Series", stack.parent().name);
        stack.pop();
        assertEquals("Inicio", stack.parent().name);
    }

    @Test
    public void savedPathKeepsEveryLevelFromRoot() {
        BrowseStack stack = new BrowseStack();
        stack.push("D:a", "Series");
        stack.push("D:b", "Temporada 1");
        List<BrowseStack.Level> decoded = BrowseStack.decode(BrowseStack.encode(stack.path()));
        assertEquals(3, decoded.size());
        assertEquals("0", decoded.get(0).id);
        assertEquals("D:a", decoded.get(1).id);
        assertEquals("Series", decoded.get(1).name);
        assertEquals("D:b", decoded.get(2).id);
        assertEquals("Temporada 1", decoded.get(2).name);
    }

    @Test
    public void decodeSkipsEntriesThatDoNotParse() {
        assertTrue(BrowseStack.decode(null).isEmpty());
        assertTrue(BrowseStack.decode("").isEmpty());
        assertTrue(BrowseStack.decode("sin separadores").isEmpty());
        List<BrowseStack.Level> kept = BrowseStack.decode("\u001Fsin id\u001E" + "D:x\u001FX");
        assertEquals(1, kept.size());
        assertEquals("D:x", kept.get(0).id);
    }

    @Test
    public void encodeReplacesControlCharactersInNames() {
        BrowseStack stack = new BrowseStack();
        stack.push("D:x", "Mal\u001Fnombre");
        List<BrowseStack.Level> decoded = BrowseStack.decode(BrowseStack.encode(stack.path()));
        assertEquals(2, decoded.size());
        assertEquals("Mal nombre", decoded.get(1).name);
    }
}
