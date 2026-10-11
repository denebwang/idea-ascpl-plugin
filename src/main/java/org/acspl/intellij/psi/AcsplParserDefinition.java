package org.acspl.intellij.psi;

import com.intellij.extapi.psi.ASTWrapperPsiElement;
import com.intellij.lang.*;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.*;
import org.acspl.intellij.AcsplLanguage;
import org.acspl.intellij.lexer.*;
import org.jetbrains.annotations.NotNull;

/** Deliberately permissive: this release does not pretend to validate controller syntax. */
public final class AcsplParserDefinition implements ParserDefinition {
    public static final IFileElementType FILE = new IFileElementType(AcsplLanguage.INSTANCE);
    public static final IElementType STATEMENT = new IElementType("STATEMENT", AcsplLanguage.INSTANCE);
    @Override public @NotNull Lexer createLexer(Project project) { return new AcsplLexer(); }
    @Override public @NotNull PsiParser createParser(Project project) {
        return (root, builder) -> {
            var file = builder.mark();
            while (!builder.eof()) {
                var statement = builder.mark();
                while (!builder.eof() && builder.getTokenType() != AcsplTokenTypes.NEWLINE
                    && builder.getTokenType() != AcsplTokenTypes.SEMICOLON) builder.advanceLexer();
                if (!builder.eof()) builder.advanceLexer();
                statement.done(STATEMENT);
            }
            file.done(root);
            return builder.getTreeBuilt();
        };
    }
    @Override public @NotNull IFileElementType getFileNodeType() { return FILE; }
    @Override public @NotNull TokenSet getWhitespaceTokens() { return TokenSet.create(TokenType.WHITE_SPACE); }
    @Override public @NotNull TokenSet getCommentTokens() { return AcsplTokenTypes.COMMENTS; }
    @Override public @NotNull TokenSet getStringLiteralElements() { return AcsplTokenTypes.STRINGS; }
    @Override public @NotNull PsiElement createElement(ASTNode node) { return new ASTWrapperPsiElement(node); }
    @Override public @NotNull PsiFile createFile(@NotNull FileViewProvider provider) { return new AcsplFile(provider); }
}
