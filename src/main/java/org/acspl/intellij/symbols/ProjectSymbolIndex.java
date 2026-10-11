package org.acspl.intellij.symbols;

import com.intellij.util.indexing.*;
import com.intellij.util.io.*;
import org.acspl.intellij.AcsplFileType;
import org.jetbrains.annotations.NotNull;
import java.util.HashMap;

/** Names only: does not infer controller buffer assignment or validate visibility. */
public final class ProjectSymbolIndex extends FileBasedIndexExtension<String, Integer> {
    public static final ID<String, Integer> NAME = ID.create("org.acspl.intellij.symbols.ProjectSymbolIndex");
    public static final int FUNCTION = 1, GLOBAL_VARIABLE = 2;
    @Override public @NotNull ID<String, Integer> getName() { return NAME; }
    @Override public @NotNull DataIndexer<String, Integer, FileContent> getIndexer() {
        return content -> {
            var result = new HashMap<String, Integer>();
            for (var declaration : DeclarationModel.scan(content.getContentAsText().toString()).declarations()) {
                if (!declaration.projectSymbol()) continue;
                int kind = declaration.kind().equals("function") ? FUNCTION : GLOBAL_VARIABLE;
                result.merge(declaration.name(), kind, (a, b) -> a | b);
            }
            return result;
        };
    }
    @Override public @NotNull KeyDescriptor<String> getKeyDescriptor() { return EnumeratorStringDescriptor.INSTANCE; }
    @Override public @NotNull DataExternalizer<Integer> getValueExternalizer() { return EnumeratorIntegerDescriptor.INSTANCE; }
    @Override public @NotNull FileBasedIndex.InputFilter getInputFilter() { return new DefaultFileTypeSpecificInputFilter(AcsplFileType.INSTANCE); }
    @Override public int getVersion() { return 1; }
    @Override public boolean dependsOnFileContent() { return true; }
}
