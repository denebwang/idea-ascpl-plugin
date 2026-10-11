package org.acspl.intellij.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

public final class AcsplCompletionContributor extends CompletionContributor implements DumbAware {
    @Override public void fillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
        var file = parameters.getOriginalFile();
        String text = file.getText();
        var completion = CompletionEngine.complete(text, parameters.getOffset(), LocalSymbols.forFile(file));
        if (completion.suppressed()) { result.stopHere(); return; }
        var filtered = result.withPrefixMatcher(completion.prefix()).caseInsensitive();
        for (var candidate : completion.candidates()) {
            var item = LookupElementBuilder.create(candidate, candidate.name())
                .withPresentableText(candidate.displayName()).withCaseSensitivity(candidate.caseSensitive())
                .withTypeText(candidate.kind()).withTailText("  " + truncate(candidate.detail()), true);
            if (candidate.function()) item = item.withInsertHandler((context, element) -> insertParentheses(context));
            filtered.addElement(item);
        }
    }
    private static String truncate(String text) {
        String oneLine = text.replaceAll("\\s+", " ");
        return oneLine.length() <= 100 ? oneLine : oneLine.substring(0, 97) + "...";
    }
    private static void insertParentheses(InsertionContext context) {
        int tail = context.getTailOffset();
        var document = context.getDocument();
        int next = tail;
        while (next < document.getTextLength() && Character.isWhitespace(document.getCharsSequence().charAt(next))) next++;
        if (next < document.getTextLength() && document.getCharsSequence().charAt(next) == '(') {
            context.getEditor().getCaretModel().moveToOffset(next + 1);
        } else {
            document.insertString(tail, "()");
            context.setTailOffset(tail + 2);
            context.getEditor().getCaretModel().moveToOffset(tail + 1);
        }
        if (context.getCompletionChar() == '(') context.setAddCompletionChar(false);
    }
}
