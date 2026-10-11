package org.acspl.intellij;

import org.acspl.intellij.symbols.DeclarationModel;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.acspl.intellij.symbols.DeclarationModel.Scope.*;

public class DeclarationModelTest {
    @Test public void distinguishesArrayDimensionsFromFunctionParameters() {
        String text = """
            GLOBAL REAL samples(3), grid(2)(3), scalar = 0
            LOCAL counter
            INT implicitLocal, indexes(4) = (1,2,3,4)
            REAL compute(REAL x, INT data(), INT matrixData()(3))
            STOP
            REAL compute(REAL x, INT data(), INT matrixData()(3)) {
                REAL temporary(5)
                RET x
            }
            """;
        var model = DeclarationModel.scan(text);
        assertVariable(model, "samples", GLOBAL, true);
        assertVariable(model, "grid", GLOBAL, true);
        assertVariable(model, "scalar", GLOBAL, false);
        assertVariable(model, "counter", LOCAL, false);
        assertVariable(model, "implicitLocal", LOCAL, false);
        assertVariable(model, "indexes", LOCAL, true);
        assertVariable(model, "temporary", LOCAL, true);
        assertEquals(2, model.declarations().stream().filter(d -> d.name().equals("compute") && d.kind().equals("function")).count());
        assertEquals(4, model.declarations().stream().filter(d -> d.array() && d.kind().equals("parameter")).count());
    }
    @Test public void functionLocalsAndParametersHideOuterNamesOnlyInsideTheirFunction() {
        String text = """
            GLOBAL INT shared(4), param
            VOID first(INT param, INT shared()) {
                shared(0) = param
                RET
            }
            VOID second() {
                param = 1
                INT shared(3)
                shared(0) = param
                RET
            }
            shared(0) = param
            """;
        var model = DeclarationModel.scan(text);
        assertEquals("parameter", model.resolve("shared", text.indexOf("shared(0)")).kind());
        assertEquals(LOCAL, model.resolve("param", text.indexOf("= param") + 2).scope());
        assertEquals(GLOBAL, model.resolve("param", text.indexOf("param = 1")).scope());
        assertEquals(LOCAL, model.resolve("shared", text.indexOf("shared(0)", text.indexOf("VOID second"))).scope());
        assertEquals(GLOBAL, model.resolve("shared", text.lastIndexOf("shared(0)")).scope());
        assertEquals(GLOBAL, model.resolve("param", text.lastIndexOf("param")).scope());
    }
    @Test public void excludesPseudoDeclarationsAndStructMembersFromProjectSymbols() {
        String text = """
            ! VOID commentFunc(INT x)
            DISP "GLOBAL INT fake"
            STRUCT Pose {
                REAL coord
                REAL method(INT x)
            }
            REAL Pose::method(INT x) { RET x }
            VOID realFunction() { RET }
            GLOBAL REAL actualGlobal(3)
            LOCAL REAL bufferArray(3)
            """;
        var model = DeclarationModel.scan(text);
        assertFalse(model.declarations().stream().anyMatch(d -> d.name().equals("commentFunc") || d.name().equals("fake")));
        assertEquals(java.util.Set.of("realFunction", "actualGlobal"),
            model.declarations().stream().filter(DeclarationModel.Declaration::projectSymbol)
                .map(DeclarationModel.Declaration::name).collect(java.util.stream.Collectors.toSet()));
    }
    @Test public void recognizesOnlyParenthesizedCallSyntaxAndPreservesCase() {
        String text = "VOID compute(INT x, REAL y)\ncompute(1, 2)\ncompute 1, 2\nCompute(1, 2)\nobj.compute(1,2)\n";
        var model = DeclarationModel.scan(text);
        assertTrue(model.callAt(text.indexOf("compute(1")));
        assertFalse(model.callAt(text.indexOf("compute 1")));
        assertNull(model.resolve("Compute", text.indexOf("Compute(")));
        assertFalse(model.callAt(text.indexOf("compute(1", text.indexOf("obj."))));
    }
    @Test public void retainsFunctionScopeAndLineNumbersDuringIncompleteEdits() {
        String text = "GLOBAL INT outer\r\nVOID f(INT argument) {\r\nREAL partial(3)\r\npartial(0) = argument\r\n";
        var model = DeclarationModel.scan(text);
        assertVariable(model, "partial", LOCAL, true);
        assertEquals(3, model.declarations().stream().filter(d -> d.name().equals("partial")).findFirst().orElseThrow().line());
        assertEquals("parameter", model.resolve("argument", text.lastIndexOf("argument")).kind());
    }
    private static void assertVariable(DeclarationModel model, String name, DeclarationModel.Scope scope, boolean array) {
        var declaration = model.declarations().stream().filter(d -> d.name().equals(name)).findFirst().orElseThrow();
        assertEquals("variable", declaration.kind());
        assertEquals(scope, declaration.scope());
        assertEquals(array, declaration.array());
    }
}
