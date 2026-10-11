package org.acspl.intellij.completion;

import com.intellij.psi.PsiFile;
import org.acspl.intellij.symbols.DeclarationModel;
import java.util.*;

/** File-level, de-duplicated inventory for completion and quick documentation. */
public final class LocalSymbols {
    public record Symbol(String name, String kind, String type, String signature, int offset, int line) {}
    private LocalSymbols() {}
    public static List<Symbol> forFile(PsiFile file) { return inventory(DeclarationModel.forFile(file)); }
    public static List<Symbol> scan(String text) { return inventory(DeclarationModel.scan(text)); }
    private static List<Symbol> inventory(DeclarationModel model) {
        var symbols = new LinkedHashMap<String, Symbol>();
        for (var d : model.declarations()) symbols.putIfAbsent(d.name(),
            new Symbol(d.name(), d.kind().equals("field") ? "variable" : d.kind(), d.type(), d.signature(), d.offset(), d.line()));
        return List.copyOf(symbols.values());
    }
}
