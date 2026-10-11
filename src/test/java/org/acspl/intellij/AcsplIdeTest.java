package org.acspl.intellij;

import com.intellij.codeInsight.CodeInsightSettings;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.platform.backend.documentation.DocumentationTargetProvider;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.acspl.intellij.completion.LocalSymbols;
import org.acspl.intellij.documentation.AcsplDocumentationTargetProvider;
import org.acspl.intellij.psi.AcsplFile;
import org.acspl.intellij.lexer.TokenScan;
import org.acspl.intellij.editor.AcsplColorSettingsPage;
import org.acspl.intellij.editor.AcsplSyntaxHighlighter;
import com.intellij.openapi.editor.markup.TextAttributes;
import java.awt.Color;

public class AcsplIdeTest extends BasePlatformTestCase {
    private CodeInsightSettings savedSettings;
    @Override protected void setUp() throws Exception {
        super.setUp();
        savedSettings = CodeInsightSettings.getInstance().clone();
        // The 2026.2 product initializes documentation defaults during fixture setup.
        // Tell the framework to use that initialized baseline, then restore it below.
        setDefaultCodeInsightSettings(savedSettings);
        CodeInsightSettings.getInstance().AUTOCOMPLETE_ON_CODE_COMPLETION = false;
    }
    @Override protected void tearDown() throws Exception {
        try { XmlSerializerUtil.copyBean(savedSettings, CodeInsightSettings.getInstance()); }
        finally { super.tearDown(); }
    }
    public void testFileTypesAndPermissivePsi() {
        for (String extension : new String[]{"prg", "acspl"}) {
            var file = myFixture.configureByText("sample." + extension, "IF incomplete\nREAL f(\n");
            assertInstanceOf(file, AcsplFile.class);
            assertSame(AcsplFileType.INSTANCE, file.getFileType());
            assertEquals("IF incomplete\nREAL f(\n", file.getText());
        }
    }
    public void testFunctionInsertionAndExistingParentheses() {
        myFixture.configureByText("sample.prg", "siN<caret>");
        myFixture.completeBasic();
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
        myFixture.checkResult("SIN(<caret>)");
        myFixture.configureByText("sample.prg", "siN<caret>(0)");
        myFixture.completeBasic();
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
        myFixture.checkResult("SIN(<caret>0)");
    }
    public void testCurrentFileCompletionAndCacheInvalidation() {
        var file = myFixture.configureByText("sample.prg", "REAL Alpha\nAl<caret>");
        assertTrue(LocalSymbols.forFile(file).stream().anyMatch(s -> s.name().equals("Alpha")));
        myFixture.completeBasic();
        assertContainsElements(myFixture.getLookupElementStrings(), "Alpha");
        myFixture.getLookup().hideLookup(true);
        WriteCommandAction.runWriteCommandAction(getProject(), () -> myFixture.getEditor().getDocument().setText("REAL Beta\nBe"));
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        assertTrue(LocalSymbols.forFile(file).stream().anyMatch(s -> s.name().equals("Beta")));
        assertFalse(LocalSymbols.forFile(file).stream().anyMatch(s -> s.name().equals("Alpha")));
    }
    public void testQualifiedConstantAndSwitchInsertion() {
        myFixture.configureByText("sample.prg", "LCI.PowerP<caret>");
        insertCompletion("LCI.PowerPWMOut");
        myFixture.checkResult("LCI.PowerPWMOut(<caret>)");
        myFixture.configureByText("sample.prg", "MST(0).#MOV<caret>");
        insertCompletion("#MOVE");
        myFixture.checkResult("MST(0).#MOVE<caret>");
        myFixture.configureByText("sample.prg", "PTP/r<caret>");
        insertCompletion("rw");
        myFixture.checkResult("PTP/rw<caret>");
    }
    private void insertCompletion(String name) {
        myFixture.completeBasic();
        var item = java.util.Arrays.stream(myFixture.getLookupElements())
            .filter(candidate -> candidate.getLookupString().equals(name)).findFirst().orElseThrow();
        myFixture.getLookup().setCurrentItem(item);
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
    }
    public void testLineCommentToggle() {
        myFixture.configureByText("sample.prg", "<caret>PTP/e 0,100");
        myFixture.performEditorAction("CommentByLineComment");
        assertTrue(myFixture.getEditor().getDocument().getText().stripLeading().startsWith("!"));
        myFixture.performEditorAction("CommentByLineComment");
        assertEquals("PTP/e 0,100", myFixture.getEditor().getDocument().getText());
    }
    public void testRegisteredHighlighterUpdatesAfterEditing() {
        myFixture.configureByText("sample.prg", "PTP/r<caret>0,100\nDISP \"!\"\nIN0.4=1");
        assertEditorTokens();
        myFixture.type("w");
        assertEditorTokens();
        WriteCommandAction.runWriteCommandAction(getProject(), () ->
            myFixture.getEditor().getDocument().setText("DISP \"unfinished\nPTP/rw0,100"));
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        assertEditorTokens();
        myFixture.configureByText("sample.prg", "<caret>LCI.PowerPWMOut(0)");
        assertEditorTokens();
        myFixture.getEditor().getSelectionModel().setSelection(0, 3);
        myFixture.type("Other");
        assertEditorTokens();
    }
    private void assertEditorTokens() {
        var editor = (EditorEx) myFixture.getEditor();
        var iterator = editor.getHighlighter().createIterator(0);
        for (var token : TokenScan.scan(editor.getDocument().getText())) {
            assertFalse(iterator.atEnd());
            assertSame(token.type(), iterator.getTokenType());
            assertEquals(token.start(), iterator.getStart());
            assertEquals(token.end(), iterator.getEnd());
            iterator.advance();
        }
        assertTrue(iterator.atEnd());
    }
    public void testUserVariableColorsAreEditableIndependently() {
        assertTrue(java.util.Arrays.stream(new AcsplColorSettingsPage().getAttributeDescriptors())
            .anyMatch(descriptor -> descriptor.getKey() == AcsplSyntaxHighlighter.IDENTIFIERS));
        String text = "REAL Alpha\nAlpha = FPOS0\n";
        myFixture.configureByText("sample.prg", text);
        var editor = (EditorEx) myFixture.getEditor();
        // The editor-bound scheme is a delegate whose clone() is unsupported.
        var scheme = (EditorColorsScheme) EditorColorsManager.getInstance().getGlobalScheme().clone();
        var userColor = new Color(0x167A8D);
        var systemColor = new Color(0xA35B16);
        var userAttributes = new TextAttributes();
        userAttributes.setForegroundColor(userColor);
        var systemAttributes = new TextAttributes();
        systemAttributes.setForegroundColor(systemColor);
        scheme.setAttributes(AcsplSyntaxHighlighter.IDENTIFIERS, userAttributes);
        scheme.setAttributes(AcsplSyntaxHighlighter.VARIABLES, systemAttributes);
        editor.setColorsScheme(scheme);
        assertTokenForeground(text.indexOf("Alpha"), userColor);
        assertTokenForeground(text.lastIndexOf("Alpha"), userColor);
        assertTokenForeground(text.indexOf("FPOS0"), systemColor);
        var changedScheme = (EditorColorsScheme) scheme.clone();
        var changedColor = new Color(0xA02C74);
        var changedAttributes = new TextAttributes();
        changedAttributes.setForegroundColor(changedColor);
        changedScheme.setAttributes(AcsplSyntaxHighlighter.IDENTIFIERS, changedAttributes);
        editor.setColorsScheme(changedScheme);
        assertTokenForeground(text.indexOf("Alpha"), changedColor);
        assertTokenForeground(text.lastIndexOf("Alpha"), changedColor);
        assertTokenForeground(text.indexOf("FPOS0"), systemColor);
    }
    private void assertTokenForeground(int offset, Color expected) {
        var iterator = ((EditorEx) myFixture.getEditor()).getHighlighter().createIterator(offset);
        assertEquals(expected, iterator.getTextAttributes().getForegroundColor());
    }
    public void testDocumentationExtensionAndPointer() {
        var file = myFixture.configureByText("sample.prg", "FPOS<caret>0");
        var extensions = ExtensionPointName.<DocumentationTargetProvider>create("com.intellij.platform.backend.documentation.targetProvider").getExtensionList();
        assertTrue(extensions.stream().anyMatch(p -> p instanceof AcsplDocumentationTargetProvider));
        var targets = new AcsplDocumentationTargetProvider().documentationTargets(file, 1);
        assertSize(1, targets);
        var pointer = targets.getFirst().createPointer();
        assertNotNull(pointer.dereference());
        assertNotNull(targets.getFirst().computeDocumentation());
        WriteCommandAction.runWriteCommandAction(getProject(), () -> myFixture.getEditor().getDocument().setText("UnknownVariable"));
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        assertNull(pointer.dereference());
    }
}
