package org.acspl.intellij;

import org.acspl.intellij.completion.*;
import org.junit.Test;
import java.util.List;
import java.util.stream.Collectors;
import static org.junit.Assert.*;

public class CompletionTest {
    private static List<String> names(String text) {
        return CompletionEngine.complete(text, text.length(), LocalSymbols.scan(text)).candidates().stream().map(CompletionEngine.Candidate::name).toList();
    }
    @Test public void recognizesDeclarationsAndSkipsText() {
        String text = """
            ! int fakeComment
            DISP "int fakeString"
            global real Alpha(3) = (1,2,3), Beta
            local Counter
            int dataGrid(2)(3), index = 0
            AXISDEF X = 0, Y = 1
            StartLabel:
            VOID f(INT axis, REAL REF value, INT data())
            STOP
            VOID f(INT axis, REAL REF value, INT data()) {
              REAL inside
              RET
            }
            STRUCT Pose { REAL x }
            Pose position
            Global static DPM_Measurement current_measurement
            """;
        var symbols = LocalSymbols.scan(text);
        var names = symbols.stream().map(LocalSymbols.Symbol::name).collect(Collectors.toSet());
        assertTrue(names.toString(), names.containsAll(List.of("Alpha", "Beta", "Counter", "dataGrid", "index", "X", "Y", "StartLabel", "f", "axis", "value", "data", "inside", "Pose", "position")));
        assertFalse(names.contains("fakeComment")); assertFalse(names.contains("fakeString"));
        assertTrue(names.contains("current_measurement"));
        assertEquals(1, symbols.stream().filter(s -> s.name().equals("f")).count());
        assertEquals("variable", symbols.stream().filter(s -> s.name().equals("dataGrid")).findFirst().orElseThrow().kind());
        assertEquals("function", symbols.stream().filter(s -> s.name().equals("f")).findFirst().orElseThrow().kind());
    }
    @Test public void respectsCaseAndConstantContext() {
        assertTrue(names("pt").contains("PTP")); assertTrue(names("siN").contains("SIN"));
        assertTrue(names("FPO").contains("FPOS")); assertFalse(names("fpo").contains("FPOS"));
        assertTrue(names("AINO").contains("AINOFFS")); assertFalse(names("aino").contains("AINOFFS"));
        assertTrue(names("REAL Alpha\nAl").contains("Alpha")); assertFalse(names("REAL Alpha\nal").contains("Alpha"));
        assertTrue(names("MST(0).#MO").contains("#MOVE"));
        assertTrue(names("#").stream().allMatch(n -> n.startsWith("#")));
    }
    @Test public void switchesAreCommandSpecificAndCombined() {
        assertTrue(names("PTP/").containsAll(List.of("e", "r", "w", "q")));
        assertFalse(names("PTP/").contains("PTP"));
        assertTrue(names("PTP/r").contains("rw")); assertFalse(names("PTP/r").contains("rr"));
        assertFalse(names("x = numerator/").contains("w"));
        assertTrue(names("LCI.PowerP").contains("LCI.PowerPWMOut"));
    }
    @Test public void suppressesStringsCharactersAndComments() {
        for (String text : List.of("! PT", "DISP \"PT", "DISP \"PT!", "n = 'P")) {
            assertTrue(CompletionEngine.complete(text, text.length(), List.of()).suppressed());
        }
        assertFalse(CompletionEngine.complete("DISP \"PT\" ", 10, List.of()).suppressed());
    }
}
