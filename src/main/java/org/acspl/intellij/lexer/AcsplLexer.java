package org.acspl.intellij.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import org.acspl.intellij.catalog.BuiltinCatalog;
import org.jetbrains.annotations.NotNull;
import static org.acspl.intellij.lexer.AcsplTokenTypes.*;

/** State describes the context BEFORE the current token, including at whitespace. */
public final class AcsplLexer extends LexerBase {
    private static final int VALUE = 1, DOT_CONTEXT = 2, COMMAND_CONTEXT = 3;
    private CharSequence buffer = "";
    private int start, end, limit, state, nextState;
    private IElementType type;
    private final BuiltinCatalog catalog = BuiltinCatalog.getInstance();

    @Override public void start(@NotNull CharSequence text, int startOffset, int endOffset, int initialState) {
        buffer = text; start = startOffset; limit = endOffset; state = initialState; locate();
    }
    @Override public int getState() { return state; }
    @Override public IElementType getTokenType() { return type; }
    @Override public int getTokenStart() { return start; }
    @Override public int getTokenEnd() { return end; }
    @Override public @NotNull CharSequence getBufferSequence() { return buffer; }
    @Override public int getBufferEnd() { return limit; }
    @Override public void advance() { start = end; state = nextState; locate(); }
    public static boolean nameStart(char c) { return c == '_' || c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z'; }
    public static boolean namePart(char c) { return nameStart(c) || c == '_' || digit(c); }
    private static boolean digit(char c) { return c >= '0' && c <= '9'; }
    private char at(int index) { return index < limit ? buffer.charAt(index) : '\0'; }
    private void locate() {
        end = start; nextState = state; type = null;
        if (start >= limit) return;
        char c = at(end++);
        if (c == '\r' || c == '\n') {
            if (c == '\r' && at(end) == '\n') end++;
            type = NEWLINE; nextState = 0; return;
        }
        if (c == ' ' || c == '\t' || c == '\f') {
            while (at(end) == ' ' || at(end) == '\t' || at(end) == '\f') end++;
            type = TokenType.WHITE_SPACE; return;
        }
        if (c == '!') {
            while (end < limit && at(end) != '\r' && at(end) != '\n') end++;
            type = COMMENT; nextState = 0; return;
        }
        if (c == '"' || c == '\'') {
            while (end < limit && at(end) != '\r' && at(end) != '\n') {
                char ch = at(end++);
                if (ch == c) break;
                if (ch == '\\' && end < limit && at(end) != '\r' && at(end) != '\n') end++;
            }
            type = c == '"' ? STRING : CHARACTER; nextState = VALUE; return;
        }
        if (c == '/' && (state & 3) == COMMAND_CONTEXT && nameStart(at(end))) {
            while (nameStart(at(end))) end++;
            type = SWITCH; return; // /rw0 must leave 0 as the first axis argument.
        }
        if (c == '#' && nameStart(at(end))) {
            while (namePart(at(end))) end++;
            type = CONSTANT; nextState = VALUE; return;
        }
        if (nameStart(c)) {
            while (namePart(at(end))) end++;
            String word = buffer.subSequence(start, end).toString();
            var entry = catalog.find(word);
            if ((state & 3) == DOT_CONTEXT) {
                String namespace = catalog.namespace(state >> 2);
                if (namespace != null) {
                    var qualified = catalog.find(namespace + "." + word);
                    if (qualified != null) entry = qualified;
                }
            }
            type = entry == null ? IDENTIFIER : switch (entry.kind()) {
                case "command" -> COMMAND; case "function" -> FUNCTION;
                case "variable" -> VARIABLE; case "constant" -> CONSTANT;
                case "type" -> TYPE; case "label" -> IDENTIFIER; default -> KEYWORD;
            };
            int id = entry != null && entry.kind().equals("command") ? catalog.commandId(entry.name()) : 0;
            nextState = id == 0 ? catalog.namespaceId(word) << 2 | VALUE : id << 2 | COMMAND_CONTEXT;
            return;
        }
        if (digit(c) || c == '.' && digit(at(end)) && (state & 3) != VALUE && (state & 3) != DOT_CONTEXT) {
            if (c == '0' && (at(end) == 'x' || at(end) == 'X' || at(end) == 'b' || at(end) == 'B')) {
                boolean binary = at(end) == 'b' || at(end) == 'B';
                if (binary ? at(end + 1) == '0' || at(end + 1) == '1' : Character.digit(at(end + 1), 16) >= 0) {
                    end++;
                    while (end < limit && (binary ? at(end) == '0' || at(end) == '1' : Character.digit(at(end), 16) >= 0)) end++;
                    type = NUMBER; nextState = VALUE; return;
                }
            }
            while (digit(at(end))) end++;
            if (c != '.' && at(end) == '.' && digit(at(end + 1))) {
                end++;
                while (digit(at(end))) end++;
            }
            if (at(end) == 'e' || at(end) == 'E') {
                int exponent = end + 1;
                if (at(exponent) == '+' || at(exponent) == '-') exponent++;
                if (digit(at(exponent))) {
                    end = exponent + 1;
                    while (digit(at(end))) end++;
                }
            }
            type = NUMBER; nextState = VALUE; return;
        }
        type = switch (c) {
            case '.' -> DOT; case ',' -> COMMA; case ';' -> SEMICOLON; case ':' -> COLON;
            case '(' -> LPAREN; case ')' -> RPAREN; case '{' -> LBRACE; case '}' -> RBRACE;
            case '+', '-', '*', '/', '%', '=', '<', '>', '&', '|', '~', '^' -> OPERATOR;
            default -> TokenType.BAD_CHARACTER;
        };
        if ((c == ':' && at(end) == ':') || (c == '+' && at(end) == '+')
            || (c == '<' && (at(end) == '<' || at(end) == '=' || at(end) == '>'))
            || (c == '>' && (at(end) == '>' || at(end) == '='))) end++;
        nextState = c == '.' ? ((state & 3) == VALUE ? state & ~3 : 0) | DOT_CONTEXT : c == ')' || c == '}' ? VALUE : 0;
    }
}
