package org.acspl.intellij.editor;

import com.intellij.codeInsight.AutoPopupController;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.acspl.intellij.completion.CompletionEngine;
import org.acspl.intellij.psi.AcsplFile;
import org.jetbrains.annotations.NotNull;
import java.util.List;

/** Request the platform's normal completion popup for ACSPL+ introducers. */
public final class AcsplTypedHandler extends TypedHandlerDelegate implements DumbAware {
    @Override public @NotNull Result checkAutoPopup(char typed, @NotNull Project project,
                                                   @NotNull Editor editor, @NotNull PsiFile file) {
        if (!(file instanceof AcsplFile) || (typed != '#' && typed != '/')) return Result.CONTINUE;
        var completion = CompletionEngine.complete(editor.getDocument().getText(),
            editor.getCaretModel().getOffset(), List.of());
        if (!completion.suppressed()) AutoPopupController.getInstance(project).scheduleAutoPopup(editor);
        return Result.STOP;
    }
}
