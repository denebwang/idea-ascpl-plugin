package org.acspl.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import java.util.ArrayList;
import java.util.List;

public final class TokenScan {
    private TokenScan() {}
    public record Token(IElementType type, String text, int start, int end, int state) {}
    public static List<Token> scan(CharSequence text) {
        var result = new ArrayList<Token>();
        var lexer = new AcsplLexer();
        lexer.start(text);
        while (lexer.getTokenType() != null) {
            result.add(new Token(lexer.getTokenType(), text.subSequence(lexer.getTokenStart(), lexer.getTokenEnd()).toString(),
                lexer.getTokenStart(), lexer.getTokenEnd(), lexer.getState()));
            lexer.advance();
        }
        return List.copyOf(result);
    }
}
