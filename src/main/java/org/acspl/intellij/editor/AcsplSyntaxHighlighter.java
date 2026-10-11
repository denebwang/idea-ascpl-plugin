package org.acspl.intellij.editor;

import com.intellij.lexer.Lexer;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.HighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.acspl.intellij.lexer.*;
import org.jetbrains.annotations.NotNull;
import java.util.Map;
import static org.acspl.intellij.lexer.AcsplTokenTypes.*;

public final class AcsplSyntaxHighlighter extends SyntaxHighlighterBase {
    private static TextAttributesKey key(String name, TextAttributesKey fallback) {
        return TextAttributesKey.createTextAttributesKey("ACSPL_" + name, fallback);
    }
    public static final TextAttributesKey KEYWORDS = key("KEYWORD", DefaultLanguageHighlighterColors.KEYWORD),
        COMMANDS = key("COMMAND", DefaultLanguageHighlighterColors.KEYWORD),
        FUNCTIONS = key("FUNCTION", DefaultLanguageHighlighterColors.FUNCTION_CALL),
        IDENTIFIERS = key("IDENTIFIER", DefaultLanguageHighlighterColors.LOCAL_VARIABLE),
        USER_FUNCTIONS = key("USER_FUNCTION", FUNCTIONS),
        GLOBAL_VARIABLES = key("GLOBAL_VARIABLE", DefaultLanguageHighlighterColors.STATIC_FIELD),
        LOCAL_VARIABLES = key("LOCAL_VARIABLE", IDENTIFIERS),
        VARIABLES = key("SYSTEM_VARIABLE", DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL),
        CONSTANTS = key("CONSTANT", DefaultLanguageHighlighterColors.CONSTANT),
        TYPES = key("TYPE", DefaultLanguageHighlighterColors.CLASS_NAME),
        NUMBERS = key("NUMBER", DefaultLanguageHighlighterColors.NUMBER),
        STRINGS = key("STRING", DefaultLanguageHighlighterColors.STRING),
        CHARACTERS = key("CHARACTER", DefaultLanguageHighlighterColors.STRING),
        COMMENTS = key("COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT),
        SWITCHES = key("SWITCH", DefaultLanguageHighlighterColors.METADATA),
        OPERATORS = key("OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN),
        BRACKETS = key("BRACKET", DefaultLanguageHighlighterColors.PARENTHESES),
        BAD = key("BAD_CHARACTER", HighlighterColors.BAD_CHARACTER);
    private static final Map<IElementType, TextAttributesKey> COLORS = Map.ofEntries(
        Map.entry(KEYWORD, KEYWORDS), Map.entry(COMMAND, COMMANDS), Map.entry(FUNCTION, FUNCTIONS),
        Map.entry(IDENTIFIER, IDENTIFIERS), Map.entry(VARIABLE, VARIABLES), Map.entry(CONSTANT, CONSTANTS), Map.entry(TYPE, TYPES),
        Map.entry(NUMBER, NUMBERS), Map.entry(STRING, STRINGS), Map.entry(CHARACTER, CHARACTERS),
        Map.entry(COMMENT, COMMENTS), Map.entry(SWITCH, SWITCHES), Map.entry(OPERATOR, OPERATORS),
        Map.entry(DOT, OPERATORS), Map.entry(LPAREN, BRACKETS), Map.entry(RPAREN, BRACKETS),
        Map.entry(LBRACE, BRACKETS), Map.entry(RBRACE, BRACKETS), Map.entry(TokenType.BAD_CHARACTER, BAD));
    @Override public @NotNull Lexer getHighlightingLexer() { return new AcsplLexer(); }
    @Override public TextAttributesKey @NotNull [] getTokenHighlights(IElementType type) { return pack(COLORS.get(type)); }
}
