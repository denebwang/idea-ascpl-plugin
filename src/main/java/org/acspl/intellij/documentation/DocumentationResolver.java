package org.acspl.intellij.documentation;

import org.acspl.intellij.catalog.BuiltinCatalog;
import org.acspl.intellij.completion.LocalSymbols;
import org.acspl.intellij.lexer.*;
import com.intellij.psi.TokenType;
import java.util.List;
import static org.acspl.intellij.lexer.AcsplTokenTypes.*;

public final class DocumentationResolver {
    public record Resolved(String name, int start, int end, BuiltinCatalog.Entry builtin, LocalSymbols.Symbol local, String switches) {}
    private DocumentationResolver() {}
    public static Resolved resolve(String text, int offset, List<LocalSymbols.Symbol> locals) {
        var tokens = TokenScan.scan(text).stream().filter(t -> t.type() != TokenType.WHITE_SPACE).toList();
        for (int i = 0; i < tokens.size(); i++) {
            var token = tokens.get(i);
            if (offset < token.start() || offset >= token.end()) continue;
            if (token.type() == SWITCH) {
                var entry = BuiltinCatalog.getInstance().command(token.state() >> 2);
                return entry == null ? null : new Resolved(entry.name() + token.text(), token.start(), token.end(), entry, null, token.text().substring(1));
            }
            if (!AcsplTokenTypes.isName(token.type())) return null;
            var entry = BuiltinCatalog.getInstance().find(token.text());
            String name = token.text();
            int start = token.start(), end = token.end();
            // Expand contiguous qualified names. Whitespace between tokens is accepted.
            int first = i, last = i;
            while (first >= 2 && tokens.get(first - 1).type() == DOT && AcsplTokenTypes.isName(tokens.get(first - 2).type())) first -= 2;
            while (last + 2 < tokens.size() && tokens.get(last + 1).type() == DOT && AcsplTokenTypes.isName(tokens.get(last + 2).type())) last += 2;
            if (first != last) {
                var qualified = new StringBuilder();
                for (int p = first; p <= last; p++) qualified.append(tokens.get(p).text());
                var full = BuiltinCatalog.getInstance().find(qualified.toString());
                if (full != null) { entry = full; name = qualified.toString(); start = tokens.get(first).start(); end = tokens.get(last).end(); }
            }
            var local = locals.stream().filter(s -> s.name().equals(token.text())).findFirst().orElse(null);
            if (local != null && token.type() == IDENTIFIER) entry = null;
            return entry == null && local == null ? null : new Resolved(name, start, end, entry, local, "");
        }
        return null;
    }
    public static String html(Resolved symbol) {
        var html = new StringBuilder("<html><body><div class='definition'><pre>");
        html.append(escape(symbol.name())).append("</pre></div><div class='content'>");
        var entry = symbol.builtin();
        if (entry != null) {
            html.append("<p>").append(escape(entry.description())).append("</p>");
            for (String signature : entry.signatures()) html.append("<pre>").append(escape(signature)).append("</pre>");
            if (!entry.parameters().isEmpty()) {
                html.append("<h4>Arguments</h4><ul>");
                for (String parameter : entry.parameters()) html.append("<li>").append(escape(parameter)).append("</li>");
                html.append("</ul>");
            }
            if (!entry.switches().isEmpty()) {
                html.append("<h4>Switches</h4><table>");
                for (var option : entry.switches()) html.append("<tr><td><code>/").append(escape(option.name()))
                    .append("</code></td><td>").append(escape(option.description())).append("</td></tr>");
                html.append("</table>");
            }
            if (!entry.owners().isEmpty()) html.append("<p>Used with: ").append(escape(String.join(", ", entry.owners()))).append(".</p>");
            html.append("<h4>ACSPL+ 3.12 manual reference</h4><ul>");
            for (var source : entry.sources()) html.append("<li>").append(escape(source.manual())).append(" — section ")
                .append(escape(source.section())).append(", page ").append(source.page()).append("</li>");
            html.append("</ul>");
        } else {
            var local = symbol.local();
            html.append("<pre>").append(escape(local.signature())).append("</pre><p>")
                .append(escape(local.kind())).append(" declared in this file at line ").append(local.line()).append(".</p>");
        }
        return html.append("</div></body></html>").toString();
    }
    public static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
}
