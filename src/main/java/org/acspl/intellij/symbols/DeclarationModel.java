package org.acspl.intellij.symbols;

import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import com.intellij.psi.TokenType;
import com.intellij.psi.util.*;
import org.acspl.intellij.catalog.BuiltinCatalog;
import org.acspl.intellij.lexer.TokenScan;
import org.acspl.intellij.lexer.TokenScan.Token;
import java.util.*;
import static org.acspl.intellij.lexer.AcsplTokenTypes.*;

/** Tolerant declaration inventory with buffer and function visibility ranges. */
public final class DeclarationModel {
    public enum Scope { GLOBAL, LOCAL, NONE }
    public record Declaration(String name, String kind, String type, String signature,
                              int offset, int line, Scope scope, boolean array,
                              int visibleStart, int visibleEnd, boolean projectSymbol) {
        public boolean variable() { return kind.equals("variable") || kind.equals("parameter"); }
        public boolean visibleAt(int position) { return visibleStart <= position && position < visibleEnd; }
    }
    private record Container(int start, int end, boolean struct) {}
    private static final Key<CachedValue<DeclarationModel>> CACHE = Key.create("acspl.declaration.model");
    private static final Set<String> PRIMITIVES = Set.of("INT", "REAL", "VOID", "MATRIX");
    private final List<Token> tokens;
    private final List<Declaration> declarations;
    private final Map<Integer, Declaration> byOffset;
    private final Map<String, List<Declaration>> byName;
    private final Map<Integer, Integer> tokenPositions;

    private DeclarationModel(List<Token> tokens, List<Declaration> declarations) {
        this.tokens = tokens;
        this.declarations = List.copyOf(declarations);
        byOffset = new HashMap<>(); byName = new HashMap<>(); tokenPositions = new HashMap<>();
        for (var declaration : declarations) {
            byOffset.put(declaration.offset(), declaration);
            byName.computeIfAbsent(declaration.name(), ignored -> new ArrayList<>()).add(declaration);
        }
        for (int i = 0; i < tokens.size(); i++) tokenPositions.put(tokens.get(i).start(), i);
    }
    public static DeclarationModel forFile(PsiFile file) {
        return CachedValuesManager.getCachedValue(file, CACHE,
            () -> CachedValueProvider.Result.create(scan(file.getText()), file));
    }
    public List<Declaration> declarations() { return declarations; }
    public Declaration declarationAt(int offset) { return byOffset.get(offset); }
    public Declaration resolve(String name, int offset) {
        // Parameters/function variables hide buffer variables, including arrays with function names.
        return byName.getOrDefault(name, List.of()).stream().filter(d -> d.visibleAt(offset))
            .min(Comparator.comparingInt((Declaration d) -> d.visibleEnd() - d.visibleStart())
                .thenComparingInt(d -> d.variable() ? 0 : 1)).orElse(null);
    }
    public boolean callAt(int offset) {
        Integer i = tokenPositions.get(offset);
        if (i == null || i + 1 >= tokens.size() || tokens.get(i + 1).type() != LPAREN) return false;
        // Member names need a receiver/type resolver; do not match unrelated project functions.
        return i == 0 || tokens.get(i - 1).type() != DOT && !tokens.get(i - 1).text().equals("::");
    }
    public boolean memberAt(int offset) {
        Integer i = tokenPositions.get(offset);
        return i != null && i > 0 && (tokens.get(i - 1).type() == DOT || tokens.get(i - 1).text().equals("::"));
    }
    public static DeclarationModel scan(String text) {
        List<Token> tokens = TokenScan.scan(text).stream()
            .filter(t -> t.type() != TokenType.WHITE_SPACE && t.type() != COMMENT).toList();
        Set<String> types = new HashSet<>();
        BuiltinCatalog.getInstance().entries().stream().filter(e -> e.kind().equals("type")).forEach(e -> types.add(e.name()));
        List<Declaration> declarations = new ArrayList<>();
        List<Container> containers = new ArrayList<>();
        int[] lines = lineStarts(text);
        for (int i = 0; i + 1 < tokens.size(); i++) {
            if (!word(tokens.get(i), "STRUCT") || tokens.get(i + 1).type() != IDENTIFIER) continue;
            Token name = tokens.get(i + 1); types.add(name.text());
            add(declarations, name, "type", "STRUCT", "STRUCT " + name.text(), Scope.NONE, false, 0, text.length(), false, lines);
            int body = skipNewlines(tokens, i + 2);
            if (body < tokens.size() && tokens.get(body).type() == LBRACE)
                containers.add(new Container(tokens.get(body).start(), bodyEnd(tokens, body, text.length()), true));
        }
        for (int i = 0; i < tokens.size(); i++) {
            Token token = tokens.get(i);
            if (i > 0 && !boundary(tokens.get(i - 1))) continue;
            Container owner = owner(containers, token.start());
            int start = owner == null ? 0 : token.start(), end = owner == null ? text.length() : owner.end();
            if ((token.type() == IDENTIFIER || word(token, "AUTOEXEC")) && i + 1 < tokens.size()
                && tokens.get(i + 1).type() == COLON && tokens.get(i + 1).text().equals(":")) {
                add(declarations, token, "label", "", token.text() + ":", Scope.NONE, false, start, end, false, lines);
            }
            if (word(token, "AXISDEF")) {
                for (int j = i + 1; j + 1 < tokens.size() && !boundary(tokens.get(j)); j++)
                    if (tokens.get(j).type() == IDENTIFIER && tokens.get(j + 1).text().equals("="))
                        add(declarations, tokens.get(j), "axis", "AXISDEF", signature(text, tokens, i), Scope.NONE, false, start, end, false, lines);
                continue;
            }
            int j = i;
            boolean explicitScope = word(token, "GLOBAL") || word(token, "LOCAL");
            Scope scope = word(token, "GLOBAL") ? Scope.GLOBAL : Scope.LOCAL;
            if (explicitScope) j = skipNewlines(tokens, j + 1);
            while (j < tokens.size() && word(tokens.get(j), "STATIC")) j++;
            String type = "INT";
            if (j < tokens.size() && isType(tokens.get(j), types)) type = tokens.get(j++).text();
            else if (!explicitScope) continue;
            j = skipNewlines(tokens, j);
            while (j < tokens.size() && (word(tokens.get(j), "FASTCALL") || word(tokens.get(j), "REF"))) j++;
            if (j < tokens.size() && word(tokens.get(j), "TAG")) {
                j++;
                if (j < tokens.size() && tokens.get(j).type() == NUMBER) j++;
            }
            if (j >= tokens.size() || tokens.get(j).type() != IDENTIFIER) continue;
            Token name = tokens.get(j);
            boolean qualified = j + 2 < tokens.size() && tokens.get(j + 1).text().equals("::") && tokens.get(j + 2).type() == IDENTIFIER;
            if (qualified) { j += 2; name = tokens.get(j); }
            int opening = j + 1;
            int close = opening < tokens.size() && tokens.get(opening).type() == LPAREN ? matching(tokens, opening, false) : -1;
            int first = skipNewlines(tokens, j + 2);
            boolean function = !explicitScope && close >= 0 && (first == close || first < close && isType(tokens.get(first), types));
            if (function) {
                String signature = text.substring(token.start(), tokens.get(close).end()).strip();
                int body = skipNewlines(tokens, close + 1);
                int functionEnd = tokens.get(close).end();
                if (body < tokens.size() && tokens.get(body).type() == LBRACE) {
                    functionEnd = bodyEnd(tokens, body, text.length());
                    containers.add(new Container(tokens.get(body).start(), functionEnd, false));
                }
                add(declarations, name, "function", type, signature, Scope.NONE, false,
                    qualified ? token.start() : owner == null ? 0 : owner.start(), qualified ? functionEnd : end,
                    owner == null && !qualified, lines);
                // Parse only parameter heads; nested array dimensions cannot declare names.
                for (int p = first; p < close;) {
                    if (isType(tokens.get(p), types)) {
                        String parameterType = tokens.get(p++).text();
                        p = skipNewlines(tokens, p);
                        if (p < close && word(tokens.get(p), "REF")) p++;
                        if (p < close && tokens.get(p).type() == IDENTIFIER) {
                            Token parameter = tokens.get(p++);
                            add(declarations, parameter, "parameter", parameterType, signature, Scope.LOCAL,
                                p < close && tokens.get(p).type() == LPAREN, token.start(), functionEnd, false, lines);
                        }
                    }
                    int depth = 0;
                    while (p < close) {
                        Token current = tokens.get(p++);
                        if (current.type() == LPAREN) depth++;
                        else if (current.type() == RPAREN) depth--;
                        else if (current.type() == COMMA && depth == 0) break;
                    }
                    p = skipNewlines(tokens, p);
                }
                i = close; continue;
            }
            String signature = signature(text, tokens, i);
            String kind = owner != null && owner.struct() ? "field" : "variable";
            if (owner != null) scope = Scope.LOCAL;
            add(declarations, name, kind, type, signature, scope, opening < tokens.size() && tokens.get(opening).type() == LPAREN,
                start, end, owner == null && scope == Scope.GLOBAL, lines);
            int depth = 0, last = j;
            for (int p = j + 1; p < tokens.size(); p++) {
                Token current = tokens.get(p);
                if (depth == 0 && boundary(current)) break;
                if (current.type() == LPAREN) depth++;
                if (current.type() == RPAREN) { if (depth == 0) break; depth--; }
                if (current.type() == COMMA && depth == 0 && p + 1 < tokens.size() && tokens.get(p + 1).type() == IDENTIFIER) {
                    Token variable = tokens.get(p + 1);
                    add(declarations, variable, kind, type, signature, scope, p + 2 < tokens.size() && tokens.get(p + 2).type() == LPAREN,
                        start, end, owner == null && scope == Scope.GLOBAL, lines);
                }
                last = p;
            }
            i = last;
        }
        return new DeclarationModel(tokens, declarations);
    }
    private static boolean isType(Token token, Set<String> types) {
        return PRIMITIVES.contains(token.text().toUpperCase(Locale.ROOT)) || types.contains(token.text());
    }
    private static boolean word(Token token, String word) { return token.text().equalsIgnoreCase(word); }
    private static boolean boundary(Token token) { return token.type() == NEWLINE || token.type() == SEMICOLON || token.type() == LBRACE || token.type() == RBRACE; }
    private static int skipNewlines(List<Token> tokens, int i) { while (i < tokens.size() && tokens.get(i).type() == NEWLINE) i++; return i; }
    private static int matching(List<Token> tokens, int opening, boolean brace) {
        int depth = 0;
        for (int i = opening; i < tokens.size(); i++) {
            if (tokens.get(i).type() == (brace ? LBRACE : LPAREN)) depth++;
            else if (tokens.get(i).type() == (brace ? RBRACE : RPAREN) && --depth == 0) return i;
            else if (!brace && (tokens.get(i).type() == LBRACE || tokens.get(i).type() == RBRACE)) return -1;
        }
        return -1;
    }
    private static int bodyEnd(List<Token> tokens, int opening, int fallback) {
        int close = matching(tokens, opening, true); return close < 0 ? fallback : tokens.get(close).end();
    }
    private static Container owner(List<Container> containers, int offset) {
        return containers.stream().filter(c -> c.start() < offset && offset < c.end())
            .min(Comparator.comparingInt(c -> c.end() - c.start())).orElse(null);
    }
    private static String signature(String text, List<Token> tokens, int first) {
        int end = tokens.get(first).end();
        for (int i = first + 1; i < tokens.size() && !boundary(tokens.get(i)); i++) end = tokens.get(i).end();
        return text.substring(tokens.get(first).start(), end).strip();
    }
    private static int[] lineStarts(String text) {
        var starts = new ArrayList<Integer>(); starts.add(0);
        for (int i = 0; i < text.length(); i++)
            if (text.charAt(i) == '\n' || text.charAt(i) == '\r' && (i + 1 == text.length() || text.charAt(i + 1) != '\n')) starts.add(i + 1);
        return starts.stream().mapToInt(Integer::intValue).toArray();
    }
    private static void add(List<Declaration> declarations, Token token, String kind, String type, String signature,
                            Scope scope, boolean array, int start, int end, boolean projectSymbol, int[] lines) {
        int line = Arrays.binarySearch(lines, token.start());
        line = line >= 0 ? line + 1 : -line - 1;
        declarations.add(new Declaration(token.text(), kind, type, signature, token.start(), line, scope, array, start, end, projectSymbol));
    }
}
