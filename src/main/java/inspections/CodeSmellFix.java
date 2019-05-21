package inspections;

import codesmell.CodeSmell;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class CodeSmellFix implements LocalQuickFix {

    private static final String CODE_SMELL_FIX = "Refactor detected code smell";

    private final CodeSmell codeSmell;

    public CodeSmellFix(CodeSmell codeSmell) {
        this.codeSmell = codeSmell;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getName() {
        return CODE_SMELL_FIX;
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
        return getName();
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiElement element = descriptor.getPsiElement();
        // Safety check to ensure no improper refactoring takes place
        if (!element.equals(this.codeSmell.getAssociatedPsiElement())) {
            return;
        }
        CommentTracker ct = this.codeSmell.getCommentTracker();
        String refactoredCode = this.codeSmell.getRefactoredCode();
        WriteCommandAction.runWriteCommandAction(project, () -> {
            ct.replaceAndRestoreComments(element, refactoredCode);
        });
    }

}
