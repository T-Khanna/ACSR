package actions;

import codesmell.CodeSmell;
import com.intellij.lang.annotation.Annotation;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import visitors.CodeVisitor;

import java.util.LinkedHashSet;
import java.util.Set;

public class CodeSmellAnnotator implements Annotator {

    private static boolean isEnabled;
    private static final Set<CodeSmell> identifiedCodeSmells;

    static {
        isEnabled = false;
        identifiedCodeSmells = new LinkedHashSet<>();
    }

    @Override
    public void annotate(@NotNull PsiElement element, @NotNull AnnotationHolder holder) {
        if (!isEnabled) {
            return;
        }
        CodeVisitor visitor = new CodeVisitor();
        element.accept(visitor);
        Set<CodeSmell> codeSmells = visitor.getIdentifiedCodeSmells();
        for (CodeSmell codeSmell : codeSmells) {
            // If this has already been identified, ignore it.
            if (identifiedCodeSmells.contains(codeSmell)) {
                continue;
            }

            TextRange textRange = codeSmell.getAssociatedPsiElement().getTextRange();
            String message = codeSmell.getAnnotationMessage();
            Annotation annotation = holder.createInfoAnnotation(textRange, message);
//            TextAttributesKey color = DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT_HIGHLIGHTED;
            TextAttributesKey color = TextAttributesKey.createTextAttributesKey("CODE_SMELL_PREEMPTIVE");
            annotation.setTextAttributes(color);
        }
    }

    public static void addIdentifiedCodeSmells(Set<CodeSmell> smellsToAdd) {
        identifiedCodeSmells.addAll(smellsToAdd);
    }

    public static void enable() {
        if (!isEnabled) {
            isEnabled = true;
        }
    }

}
