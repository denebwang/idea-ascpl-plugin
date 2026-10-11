package org.acspl.intellij.editor;

import com.intellij.lang.annotation.*;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.*;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.*;
import com.intellij.util.indexing.FileBasedIndex;
import org.acspl.intellij.lexer.TokenScan;
import org.acspl.intellij.symbols.*;
import org.jetbrains.annotations.NotNull;
import java.util.*;
import static org.acspl.intellij.editor.AcsplSyntaxHighlighter.*;
import static org.acspl.intellij.lexer.AcsplTokenTypes.IDENTIFIER;

public final class AcsplSemanticAnnotator implements Annotator {
    private static final Key<CachedValue<Map<Integer, TextAttributesKey>>> CACHE = Key.create("acspl.semantic.colors");
    @Override public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (element.getNode().getElementType() != IDENTIFIER) return;
        SymbolHighlightingRefresh.getInstance(element.getProject());
        var key = colors(element.getContainingFile()).get(element.getTextRange().getStartOffset());
        if (key != null) holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(element).textAttributes(key).create();
    }
    private static Map<Integer, TextAttributesKey> colors(PsiFile file) {
        var project = file.getProject();
        ModificationTracker indexTracker = () -> FileBasedIndex.getInstance().getIndexModificationStamp(ProjectSymbolIndex.NAME, project);
        return CachedValuesManager.getCachedValue(file, CACHE, () -> CachedValueProvider.Result.create(computeColors(file),
            file, PsiModificationTracker.MODIFICATION_COUNT, ProjectRootModificationTracker.getInstance(project),
            VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS, indexTracker));
    }
    private static Map<Integer, TextAttributesKey> computeColors(PsiFile file) {
        DeclarationModel model = DeclarationModel.forFile(file);
        var result = new HashMap<Integer, TextAttributesKey>();
        var projectKinds = new HashMap<String, Integer>();
        for (var token : TokenScan.scan(file.getText())) {
            if (token.type() != IDENTIFIER) continue;
            var declaration = model.declarationAt(token.start());
            if (declaration == null && model.memberAt(token.start())) continue;
            if (declaration == null) declaration = model.resolve(token.text(), token.start());
            TextAttributesKey key = null;
            if (declaration != null) {
                if (declaration.variable()) key = declaration.scope() == DeclarationModel.Scope.GLOBAL ? GLOBAL_VARIABLES : LOCAL_VARIABLES;
                else if (declaration.kind().equals("function") && (model.declarationAt(token.start()) != null || model.callAt(token.start()))) key = USER_FUNCTIONS;
                // A known non-function prevents project fallback, including arrays.
            } else {
                int kinds = projectKinds.computeIfAbsent(token.text(), name -> {
                    int[] found = {0};
                    FileBasedIndex.getInstance().processValues(ProjectSymbolIndex.NAME, name, null,
                        (source, value) -> { found[0] |= value; return true; }, GlobalSearchScope.projectScope(file.getProject()));
                    return found[0];
                });
                if ((kinds & ProjectSymbolIndex.GLOBAL_VARIABLE) != 0) key = GLOBAL_VARIABLES;
                else if ((kinds & ProjectSymbolIndex.FUNCTION) != 0 && model.callAt(token.start())) key = USER_FUNCTIONS;
            }
            if (key != null) result.put(token.start(), key);
        }
        return Map.copyOf(result);
    }
}
