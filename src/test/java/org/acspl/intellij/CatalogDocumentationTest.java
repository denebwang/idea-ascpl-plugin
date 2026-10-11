package org.acspl.intellij;

import org.acspl.intellij.catalog.BuiltinCatalog;
import org.acspl.intellij.completion.LocalSymbols;
import org.acspl.intellij.documentation.DocumentationResolver;
import org.junit.Test;
import java.util.HashSet;
import java.util.List;
import static org.junit.Assert.*;

public class CatalogDocumentationTest {
    @Test public void catalogHasValidSourcesAndUniqueEntries() {
        var entries = BuiltinCatalog.getInstance().entries();
        assertTrue(entries.size() > 700);
        var keys = new HashSet<String>();
        for (var entry : entries) {
            assertTrue(entry.name(), keys.add(entry.name()));
            assertFalse(entry.name(), entry.description().isBlank());
            assertFalse(entry.name(), entry.signatures().isEmpty());
            assertFalse(entry.name(), entry.sources().isEmpty());
            for (var source : entry.sources()) {
                assertTrue(source.page() > 0);
                assertTrue(source.page() <= (source.manual().contains("Programmers") ? 404 : 1028));
            }
        }
        assertNull(BuiltinCatalog.getInstance().find("#VGV"));
        // #RUN is a legitimate PST bit constant, despite sharing a terminal name.
        assertEquals("constant", BuiltinCatalog.getInstance().find("#RUN").kind());
        assertEquals("label", BuiltinCatalog.getInstance().find("AUTOEXEC").kind());
        assertNull(BuiltinCatalog.getInstance().find("autoexec"));
    }
    @Test public void keyDocumentationIsGroundedInManuals() {
        for (String text : List.of("PTP", "DISP", "FPOS0", "MST(0).#MOVE", "LCI.PowerPWMOut",
            "DPM_Measurement.MeasureProcess", "MotionDuration.CalculatePTPDuration")) {
            int offset = text.equals("MST(0).#MOVE") ? text.indexOf('#') : text.length() - 1;
            var target = DocumentationResolver.resolve(text, offset, List.of());
            assertNotNull(text, target);
            String html = DocumentationResolver.html(target);
            assertTrue(html.contains("3.12")); assertTrue(html.contains("page"));
        }
        var fpos = DocumentationResolver.resolve("FPOS0", 2, List.of());
        assertEquals("FPOS", fpos.builtin().name());
        assertTrue(DocumentationResolver.html(fpos).contains("Read-only"));
        assertTrue(DocumentationResolver.html(DocumentationResolver.resolve("PTP/rw0,1", 5, List.of())).contains("relative"));
    }
    @Test public void localDocumentationEscapesSourceCode() {
        String text = "INT Foo = (2 < 3)\nFoo";
        var target = DocumentationResolver.resolve(text, text.length() - 1, LocalSymbols.scan(text));
        assertNotNull(target.local());
        assertTrue(DocumentationResolver.html(target).contains("2 &lt; 3"));
        assertFalse(DocumentationResolver.html(target).contains("2 < 3"));
    }
}
