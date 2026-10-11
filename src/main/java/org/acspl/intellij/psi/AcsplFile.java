package org.acspl.intellij.psi;

import com.intellij.extapi.psi.PsiFileBase;
import com.intellij.psi.FileViewProvider;
import com.intellij.openapi.fileTypes.FileType;
import org.acspl.intellij.AcsplFileType;
import org.acspl.intellij.AcsplLanguage;
import org.jetbrains.annotations.NotNull;

public final class AcsplFile extends PsiFileBase {
    public AcsplFile(@NotNull FileViewProvider provider) { super(provider, AcsplLanguage.INSTANCE); }
    @Override public @NotNull FileType getFileType() { return AcsplFileType.INSTANCE; }
    @Override public String toString() { return "ACSPL+ file"; }
}
