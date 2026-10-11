package org.acspl.intellij.editor;

import com.intellij.lang.Commenter;

public final class AcsplCommenter implements Commenter {
    @Override public String getLineCommentPrefix() { return "!"; }
    @Override public String getBlockCommentPrefix() { return null; }
    @Override public String getBlockCommentSuffix() { return null; }
    @Override public String getCommentedBlockCommentPrefix() { return null; }
    @Override public String getCommentedBlockCommentSuffix() { return null; }
}
