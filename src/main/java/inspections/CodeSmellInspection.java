package inspections;

import codesmell.slowloop.SlowLoopCodeSmell;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiForStatement;
import detection.DetectSlowLoop;
import org.jetbrains.annotations.NotNull;

public class CodeSmellInspection extends AbstractBaseJavaLocalInspectionTool {

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        // Need to create separate visitor as the SourceCodeVisitor is recursive, which this inspection
        // cannot return at this point.
        return new JavaElementVisitor() {
            @Override
            public void visitForStatement(PsiForStatement forStatement) {
                SlowLoopCodeSmell possibleSlowLoop = DetectSlowLoop.checkForSlowLoop(forStatement);
                if (possibleSlowLoop != null) {
                    holder.registerProblem(possibleSlowLoop.getAssociatedPsiElement(), possibleSlowLoop.getShortDescription(), new CodeSmellFix(possibleSlowLoop));
                }
                super.visitForStatement(forStatement);
            }
        };
    }

}