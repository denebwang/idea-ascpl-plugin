package org.acspl.intellij.completion;

import org.acspl.intellij.catalog.BuiltinCatalog;
import org.acspl.intellij.lexer.*;
import java.util.*;
import static org.acspl.intellij.lexer.AcsplTokenTypes.*;

/** UI-independent completion rules, exercised by unit and IDE fixture tests. */
public final class CompletionEngine {
    public record Candidate(String name, String displayName, String kind, String detail, boolean function, boolean caseSensitive) {}
    public record Result(String prefix, List<Candidate> candidates, boolean suppressed) {}
    private CompletionEngine() {}

    public static Result complete(String text, int offset, List<LocalSymbols.Symbol> locals) {
        offset = Math.max(0, Math.min(offset, text.length()));
        var tokens = TokenScan.scan(text);
        for (var token : tokens) {
            if (token.start() <= offset && offset <= token.end() && (token.type() == COMMENT
                || (token.type() == STRING || token.type() == CHARACTER) && (offset < token.end()
                    || token.text().length() < 2 || token.text().charAt(token.text().length() - 1) != token.text().charAt(0)))) {
                return new Result("", List.of(), true);
            }
        }
        int start = offset;
        while (start > 0 && AcsplLexer.namePart(text.charAt(start - 1))) start--;
        var catalog = BuiltinCatalog.getInstance();
        if (start > 0 && text.charAt(start - 1) == '/') {
            int slash = start - 1;
            var atSlash = tokens.stream().filter(t -> t.start() <= slash && t.end() > slash).findFirst().orElse(null);
            if (atSlash != null && (atSlash.state() & 3) == 3) {
                var command = catalog.command(atSlash.state() >> 2);
                if (command != null) {
                    String prefix = text.substring(start, offset);
                    var options = new ArrayList<Candidate>();
                    for (var option : command.switches()) {
                        if (prefix.equalsIgnoreCase(option.name())) options.add(new Candidate(prefix, "/" + option.name(), "switch", option.description(), false, false));
                        else if (!prefix.toLowerCase(Locale.ROOT).contains(option.name().toLowerCase(Locale.ROOT))) {
                            options.add(new Candidate(prefix + option.name(), "/" + option.name(), "switch", option.description(), false, false));
                        }
                    }
                    return new Result(prefix, List.copyOf(options), false);
                }
            }
        }
        boolean constant = start > 0 && text.charAt(start - 1) == '#';
        if (constant) start--;
        // Qualified built-ins, e.g. LCI.PowerPWMOut, replace the whole qualified prefix.
        if (!constant) {
            int qualified = start;
            while (qualified > 0 && (AcsplLexer.namePart(text.charAt(qualified - 1)) || text.charAt(qualified - 1) == '.')) qualified--;
            String dotted = text.substring(qualified, offset);
            if (dotted.contains(".") && catalog.entries().stream().anyMatch(e -> e.name().regionMatches(true, 0, dotted, 0, dotted.length()))) start = qualified;
        }
        String prefix = text.substring(start, offset);
        var candidates = new LinkedHashMap<String, Candidate>();
        for (var entry : catalog.entries()) {
            if (constant != entry.kind().equals("constant")) continue;
            if (matches(entry.name(), prefix, entry.caseSensitive())) candidates.putIfAbsent(entry.name(),
                new Candidate(entry.name(), entry.name(), entry.kind(), entry.signature(), entry.function(), entry.caseSensitive()));
        }
        if (!constant) for (var local : locals) {
            if (local.name().startsWith(prefix)) candidates.put(local.name(), new Candidate(local.name(), local.name(), local.kind(),
                local.signature(), local.kind().equals("function"), true));
        }
        return new Result(prefix, List.copyOf(candidates.values()), false);
    }
    private static boolean matches(String name, String prefix, boolean sensitive) {
        return sensitive ? name.startsWith(prefix) : name.regionMatches(true, 0, prefix, 0, prefix.length());
    }
}
