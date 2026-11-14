package cl.streambox.tv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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
}
