package org.acspl.intellij;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.colors.*;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.testFramework.IndexingTestUtil;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.acspl.intellij.editor.AcsplColorSettingsPage;
import java.awt.Color;
import java.util.*;
import static org.acspl.intellij.editor.AcsplSyntaxHighlighter.*;

public class SemanticHighlightingTest extends BasePlatformTestCase {
    public void testCrossFileFunctionCallsAndGlobalArrays() {
        myFixture.addFileToProject("library.acspl", """
            VOID crossFunction(INT axis) { RET }
            GLOBAL REAL globalArray(3)
            LOCAL REAL privateArray(3)
            ! VOID fakeFunction(INT x)
            DISP "VOID stringFunction(INT x)"
            STRUCT Example { VOID method(INT x) }
            """);
        String text = """
            crossFunction(1)
            crossFunction 1
            CrossFunction(1)
            globalArray(0) = 2
            privateArray(0) = 3
            fakeFunction(1)
            stringFunction(1)
            method(1)
            obj.crossFunction(1)
            missing(1)
            """;
        myFixture.configureByText("main.prg", text);
        IndexingTestUtil.waitUntilIndexesAreReady(getProject());
        var colors = semanticColors();
        assertSame(USER_FUNCTIONS, colors.get(text.indexOf("crossFunction")));
        assertNull(colors.get(text.indexOf("crossFunction 1")));
        assertNull(colors.get(text.indexOf("CrossFunction")));
        assertSame(GLOBAL_VARIABLES, colors.get(text.indexOf("globalArray")));
        for (String name : List.of("privateArray", "fakeFunction", "stringFunction", "method", "missing"))
            assertNull(name, colors.get(text.indexOf(name)));
        assertNull(colors.get(text.lastIndexOf("crossFunction")));
    }
    public void testArraysAndParametersTakePrecedenceOverOtherFileFunctions() {
        myFixture.addFileToProject("library.prg", "REAL samples(INT x) { RET x }\nREAL data(INT x) { RET x }\n");
        String text = """
            GLOBAL REAL samples(3)
            LOCAL INT data(2)(3), count
            VOID useArray(INT samples(), REAL data()(3)) {
                samples(0) = data(0)(0)
                RET
            }
            samples(0) = data(0)(0)
            """;
        myFixture.configureByText("main.acspl", text);
        var colors = semanticColors();
        assertAllOccurrences(text, colors, "samples", List.of(GLOBAL_VARIABLES, LOCAL_VARIABLES, LOCAL_VARIABLES, GLOBAL_VARIABLES));
        assertAllOccurrences(text, colors, "data", List.of(LOCAL_VARIABLES, LOCAL_VARIABLES, LOCAL_VARIABLES, LOCAL_VARIABLES));
        assertSame(USER_FUNCTIONS, colors.get(text.indexOf("useArray")));
    }
    public void testScopesDeclarationsReferencesAndIndependentColors() {
        String text = """
            GLOBAL REAL shared
            INT bufferLocal
            VOID showValue(INT shared) {
                REAL temporary
                temporary = shared + bufferLocal
                RET
            }
            shared = 1
            showValue(shared)
            ! shared showValue temporary
            DISP "shared showValue temporary"
            """;
        myFixture.configureByText("main.prg", text);
        var colors = semanticColors();
        int body = text.indexOf("VOID");
        assertSame(GLOBAL_VARIABLES, colors.get(text.indexOf("shared")));
        assertSame(LOCAL_VARIABLES, colors.get(text.indexOf("shared", body)));
        assertSame(LOCAL_VARIABLES, colors.get(text.indexOf("shared +")));
        assertSame(GLOBAL_VARIABLES, colors.get(text.indexOf("shared =")));
        assertSame(LOCAL_VARIABLES, colors.get(text.indexOf("temporary =")));
        assertSame(USER_FUNCTIONS, colors.get(text.indexOf("showValue(shared)")));
        assertNull(colors.get(text.indexOf("shared showValue")));
        var descriptors = Arrays.stream(new AcsplColorSettingsPage().getAttributeDescriptors()).map(d -> d.getKey()).toList();
        assertTrue(descriptors.containsAll(List.of(USER_FUNCTIONS, GLOBAL_VARIABLES, LOCAL_VARIABLES)));
        var scheme = (EditorColorsScheme) EditorColorsManager.getInstance().getGlobalScheme().clone();
        var palette = Map.of(USER_FUNCTIONS, new Color(0x9134A0), GLOBAL_VARIABLES, new Color(0xD88216), LOCAL_VARIABLES, new Color(0x167A8D));
        palette.forEach((key, color) -> { var attributes = new TextAttributes(); attributes.setForegroundColor(color); scheme.setAttributes(key, attributes); });
        ((com.intellij.openapi.editor.ex.EditorEx) myFixture.getEditor()).setColorsScheme(scheme);
        semanticColors();
        for (var info : myFixture.doHighlighting(HighlightSeverity.INFORMATION)) {
            var highlighter = info.getHighlighter();
            if (highlighter == null) continue;
            Color expected = palette.get(highlighter.getTextAttributesKey());
            if (expected != null) assertEquals(expected, highlighter.getTextAttributes(scheme).getForegroundColor());
        }
        assertEquals(3, new AcsplColorSettingsPage().getAdditionalHighlightingTagToDescriptorMap().size());
    }
    public void testCrossFileUnsavedEditAndDeletionInvalidateHighlighting() {
        var library = myFixture.addFileToProject("library.prg", "VOID remoteFunc(INT arg) { RET }\n");
        String text = "remoteFunc(1)\nrenamedFunc(1)\n";
        myFixture.configureByText("main.prg", text);
        assertSame(USER_FUNCTIONS, semanticColors().get(0));
        var libraryDocument = PsiDocumentManager.getInstance(getProject()).getDocument(library);
        assertNotNull(libraryDocument);
        WriteCommandAction.runWriteCommandAction(getProject(), () -> libraryDocument.setText("VOID renamedFunc(INT arg) { RET }\n"));
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        var changed = semanticColors();
        assertNull(changed.get(0));
        assertSame(USER_FUNCTIONS, changed.get(text.indexOf("renamedFunc")));
        WriteCommandAction.runWriteCommandAction(getProject(), library::delete);
        IndexingTestUtil.waitUntilIndexesAreReady(getProject());
        assertTrue(semanticColors().isEmpty());
    }
    public void testLocalArrayEditChangesFunctionCallColor() {
        myFixture.addFileToProject("library.acspl", "VOID remoteFunc(INT arg) { RET }\n");
        myFixture.configureByText("main.prg", "remoteFunc(0)\n");
        assertSame(USER_FUNCTIONS, semanticColors().get(0));
        WriteCommandAction.runWriteCommandAction(getProject(), () ->
            myFixture.getEditor().getDocument().setText("LOCAL INT remoteFunc(3)\nremoteFunc(0)\n"));
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        var colors = semanticColors();
        assertSame(LOCAL_VARIABLES, colors.get(myFixture.getFile().getText().lastIndexOf("remoteFunc")));
    }
    private Map<Integer, TextAttributesKey> semanticColors() {
        IndexingTestUtil.waitUntilIndexesAreReady(getProject());
        return currentSemanticColors();
    }
    public void testAddingLibraryAfterConsumerIsAlreadyHighlighted() {
        myFixture.configureByText("main.prg", "newFunction(0)\n");
        assertTrue(semanticColors().isEmpty());
        myFixture.addFileToProject("new-library.acspl", "VOID newFunction(INT arg) { RET }\n");
        assertSame(USER_FUNCTIONS, semanticColors().get(0));
    }
    private Map<Integer, TextAttributesKey> currentSemanticColors() {
        IndexingTestUtil.waitUntilIndexesAreReady(getProject());
        var result = new HashMap<Integer, TextAttributesKey>();
        for (var info : myFixture.doHighlighting(HighlightSeverity.INFORMATION)) {
            var highlighter = info.getHighlighter();
            if (highlighter == null) continue;
            var key = highlighter.getTextAttributesKey();
            if (key == USER_FUNCTIONS || key == GLOBAL_VARIABLES || key == LOCAL_VARIABLES)
                result.put(highlighter.getStartOffset(), key);
        }
        return result;
    }
    private static void assertAllOccurrences(String text, Map<Integer, TextAttributesKey> colors, String name, List<TextAttributesKey> expected) {
        int start = 0;
        for (var key : expected) {
            int offset = text.indexOf(name, start);
            assertTrue(offset >= 0);
            assertSame("at " + offset, key, colors.get(offset));
            start = offset + name.length();
        }
    }
}
