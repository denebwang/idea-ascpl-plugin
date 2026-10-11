package org.acspl.intellij.editor;

import com.intellij.openapi.options.colors.*;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import org.acspl.intellij.AcsplFileType;
import org.jetbrains.annotations.NotNull;
import javax.swing.Icon;
import java.util.Map;
import static org.acspl.intellij.editor.AcsplSyntaxHighlighter.*;

public final class AcsplColorSettingsPage implements ColorSettingsPage {
    private static final AttributesDescriptor[] DESCRIPTORS = {
        new AttributesDescriptor("Keyword", KEYWORDS), new AttributesDescriptor("Command", COMMANDS),
        new AttributesDescriptor("Built-in function", FUNCTIONS), new AttributesDescriptor("System variable", VARIABLES),
        new AttributesDescriptor("User variable / identifier", IDENTIFIERS),
        new AttributesDescriptor("User-defined function", USER_FUNCTIONS),
        new AttributesDescriptor("GLOBAL variable", GLOBAL_VARIABLES), new AttributesDescriptor("LOCAL variable / parameter", LOCAL_VARIABLES),
        new AttributesDescriptor("Symbolic constant", CONSTANTS), new AttributesDescriptor("Type", TYPES),
        new AttributesDescriptor("Number", NUMBERS), new AttributesDescriptor("String", STRINGS),
        new AttributesDescriptor("Character constant", CHARACTERS), new AttributesDescriptor("Comment", COMMENTS),
        new AttributesDescriptor("Command switch", SWITCHES), new AttributesDescriptor("Operator", OPERATORS),
        new AttributesDescriptor("Bracket", BRACKETS), new AttributesDescriptor("Unrecognized character", BAD)};
    @Override public Icon getIcon() { return AcsplFileType.INSTANCE.getIcon(); }
    @Override public @NotNull SyntaxHighlighter getHighlighter() { return new AcsplSyntaxHighlighter(); }
    @Override public @NotNull String getDemoText() {
        return """
            ! ACSPL+ 3.12 — motion and expression examples
            GLOBAL REAL <global>destination</global>
            LOCAL INT <local>mask</local> = 0b111
            <global>destination</global> = SIN(1.25e-3) * 0x10
            AUTOEXEC:
            ENABLE 0
            PTP/rw 0, <global>destination</global>
            DISP "Position ! %f\\n", FPOS0
            TILL ^MST(0).#MOVE
            <local>mask</local> = 'A'
            <function>showPosition</function>(0)
            STOP
            VOID <function>showPosition</function>(INT <local>axis</local>) {
                DISP FPOS(<local>axis</local>)
                RET
            }
            """;
    }
    @Override public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() {
        return Map.of("function", USER_FUNCTIONS, "global", GLOBAL_VARIABLES, "local", LOCAL_VARIABLES);
    }
    @Override public AttributesDescriptor @NotNull [] getAttributeDescriptors() { return DESCRIPTORS; }
    @Override public ColorDescriptor @NotNull [] getColorDescriptors() { return ColorDescriptor.EMPTY_ARRAY; }
    @Override public @NotNull String getDisplayName() { return "ACSPL+"; }
}
