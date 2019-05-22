package inspections;

import codesmell.CodeSmell;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import utils.Utils;

import java.util.Map;

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
        PsiElement descriptorElement = descriptor.getPsiElement();
        // Safety check to ensure no improper refactoring takes place
        Map<PsiElement, String> refactoringMapping = this.codeSmell.getMappingFromPsiElementToRefactoring();
        if (!refactoringMapping.containsKey(descriptorElement)) {
            return;
        }

        WriteCommandAction.runWriteCommandAction(project, () -> {
            Utils.refactorCodeSegment(project, refactoringMapping);
        });
    }

}
