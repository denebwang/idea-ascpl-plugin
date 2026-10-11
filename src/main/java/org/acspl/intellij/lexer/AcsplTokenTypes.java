package org.acspl.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.acspl.intellij.AcsplLanguage;

public final class AcsplTokenTypes {
    private AcsplTokenTypes() {}
    private static IElementType token(String name) { return new IElementType(name, AcsplLanguage.INSTANCE); }
    public static final IElementType IDENTIFIER = token("IDENTIFIER"), KEYWORD = token("KEYWORD"),
        COMMAND = token("COMMAND"), FUNCTION = token("FUNCTION"), VARIABLE = token("SYSTEM_VARIABLE"),
        CONSTANT = token("CONSTANT"), TYPE = token("TYPE"), NUMBER = token("NUMBER"),
        STRING = token("STRING"), CHARACTER = token("CHARACTER"), COMMENT = token("COMMENT"),
        SWITCH = token("SWITCH"), OPERATOR = token("OPERATOR"), DOT = token("DOT"),
        COMMA = token("COMMA"), COLON = token("COLON"), SEMICOLON = token("SEMICOLON"),
        LPAREN = token("LPAREN"), RPAREN = token("RPAREN"), LBRACE = token("LBRACE"),
        RBRACE = token("RBRACE"), NEWLINE = token("NEWLINE");
    public static final TokenSet COMMENTS = TokenSet.create(COMMENT);
    public static final TokenSet STRINGS = TokenSet.create(STRING, CHARACTER);
    public static boolean isName(IElementType type) {
        return type == IDENTIFIER || type == KEYWORD || type == COMMAND || type == FUNCTION
            || type == VARIABLE || type == CONSTANT || type == TYPE;
    }
}
