package org.acspl.intellij;

import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NotNull;
import javax.swing.Icon;

public final class AcsplFileType extends LanguageFileType {
    public static final AcsplFileType INSTANCE = new AcsplFileType();
    private AcsplFileType() { super(AcsplLanguage.INSTANCE); }
    @Override public @NotNull String getName() { return "ACSPL+"; }
    @Override public @NotNull String getDescription() { return "ACSPL+ motion-control script"; }
    @Override public @NotNull String getDefaultExtension() { return "prg"; }
    @Override public Icon getIcon() { return IconLoader.getIcon("/icons/acspl.svg", AcsplFileType.class); }
}
