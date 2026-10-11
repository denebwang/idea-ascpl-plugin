package org.acspl.intellij.symbols;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.*;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.*;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiManager;
import org.acspl.intellij.AcsplFileType;
import org.jetbrains.annotations.NotNull;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Recolors open consumers after unsaved library edits and file-system changes. */
public final class SymbolHighlightingRefresh implements Disposable {
    private final Project project;
    private final AtomicBoolean scheduled = new AtomicBoolean();
    public static SymbolHighlightingRefresh getInstance(Project project) { return project.getService(SymbolHighlightingRefresh.class); }
    public SymbolHighlightingRefresh(Project project) {
        this.project = project;
        EditorFactory.getInstance().getEventMulticaster().addDocumentListener(new DocumentListener() {
            @Override public void documentChanged(@NotNull DocumentEvent event) {
                VirtualFile file = FileDocumentManager.getInstance().getFile(event.getDocument());
                if (file != null && script(file.getName())) schedule();
            }
        }, this);
        project.getMessageBus().connect(this).subscribe(VirtualFileManager.VFS_CHANGES, new BulkFileListener() {
            @Override public void after(@NotNull List<? extends VFileEvent> events) {
                for (var event : events) {
                    if (script(event.getPath()) || event instanceof VFilePropertyChangeEvent change
                        && VirtualFile.PROP_NAME.equals(change.getPropertyName()) && script(String.valueOf(change.getOldValue()))) {
                        schedule(); break;
                    }
                }
            }
        });
    }
    private static boolean script(String name) {
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.endsWith(".prg") || lower.endsWith(".acspl");
    }
    private void schedule() {
        if (project.isDisposed() || !scheduled.compareAndSet(false, true)) return;
        ApplicationManager.getApplication().invokeLater(() -> {
            scheduled.set(false);
            if (!project.isDisposed()) PsiDocumentManager.getInstance(project).performWhenAllCommitted(() -> {
                if (project.isDisposed()) return;
                for (VirtualFile file : FileEditorManager.getInstance(project).getOpenFiles()) {
                    if (file.getFileType() != AcsplFileType.INSTANCE) continue;
                    var psiFile = PsiManager.getInstance(project).findFile(file);
                    if (psiFile != null) DaemonCodeAnalyzer.getInstance(project).restart(psiFile, "ACSPL+ project symbols changed");
                }
            });
        }, project.getDisposed());
    }
    @Override public void dispose() {}
}
