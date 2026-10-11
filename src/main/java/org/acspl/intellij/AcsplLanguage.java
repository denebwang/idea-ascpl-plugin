package org.acspl.intellij;

import com.intellij.lang.Language;

public final class AcsplLanguage extends Language {
    public static final AcsplLanguage INSTANCE = new AcsplLanguage();
    private AcsplLanguage() { super("ACSPL+"); }
}
