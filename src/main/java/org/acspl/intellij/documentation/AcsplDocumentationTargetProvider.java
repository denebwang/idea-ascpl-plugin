package org.acspl.intellij.documentation;

import com.intellij.model.Pointer;
import com.intellij.openapi.util.TextRange;
import com.intellij.platform.backend.documentation.*;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.PsiFile;
import org.acspl.intellij.AcsplFileType;
import org.acspl.intellij.completion.LocalSymbols;
import org.acspl.intellij.psi.AcsplFile;
import org.jetbrains.annotations.NotNull;
import java.util.List;

public final class AcsplDocumentationTargetProvider implements DocumentationTargetProvider {
    @Override public @NotNull List<DocumentationTarget> documentationTargets(@NotNull PsiFile file, int offset) {
        if (!(file instanceof AcsplFile)) return List.of();
        var resolved = DocumentationResolver.resolve(file.getText(), offset, LocalSymbols.forFile(file));
        return resolved == null ? List.of() : List.of(new Target(file, resolved));
    }
    private record Target(PsiFile file, DocumentationResolver.Resolved resolved) implements DocumentationTarget {
        @Override public @NotNull Pointer<? extends DocumentationTarget> createPointer() {
            // Track the file range across edits and reject a changed or removed symbol.
            String expected = resolved.name();
            return Pointer.fileRangePointer(file, new TextRange(resolved.start(), resolved.end()), (restoredFile, range) -> {
                var current = DocumentationResolver.resolve(restoredFile.getText(), range.getStartOffset(), LocalSymbols.forFile(restoredFile));
                return current != null && current.name().equals(expected) ? new Target(restoredFile, current) : null;
            });
        }
        @Override public @NotNull TargetPresentation computePresentation() {
            return TargetPresentation.builder(resolved.name()).icon(AcsplFileType.INSTANCE.getIcon())
                .locationText(resolved.builtin() == null ? file.getName() : "ACSPL+ 3.12").presentation();
        }
        @Override public DocumentationResult computeDocumentation() { return DocumentationResult.documentation(DocumentationResolver.html(resolved)); }
        @Override public String computeDocumentationHint() {
            return "<html>" + DocumentationResolver.escape(resolved.builtin() == null ? resolved.local().signature() : resolved.builtin().description()) + "</html>";
        }
    }
}
