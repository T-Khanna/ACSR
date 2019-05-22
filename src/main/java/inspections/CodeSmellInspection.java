package inspections;

import codesmell.CodeSmell;
import codesmell.heavyasynctask.HeavyAsyncTaskCodeSmell;
import codesmell.slowloop.SlowLoopCodeSmell;
import com.intellij.codeInspection.AbstractBaseJavaLocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import detection.DetectHeavyAsyncTask;
import detection.DetectSlowLoop;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class CodeSmellInspection extends AbstractBaseJavaLocalInspectionTool {

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        // Need to create separate visitor as the SourceCodeVisitor is recursive, which this inspection
        // cannot return at this point.
        return new JavaElementVisitor() {
            @Override
            public void visitClass(PsiClass aClass) {
                HeavyAsyncTaskCodeSmell possibleHeavyAsyncTask = DetectHeavyAsyncTask.checkForHeavyAsyncTask(aClass);
                registerCodeSmell(possibleHeavyAsyncTask, holder);
                super.visitClass(aClass);
            }

            @Override
            public void visitForStatement(PsiForStatement forStatement) {
                SlowLoopCodeSmell possibleSlowLoop = DetectSlowLoop.checkForSlowLoop(forStatement);
                registerCodeSmell(possibleSlowLoop, holder);
                super.visitForStatement(forStatement);
            }
        };
    }

    private void registerCodeSmell(CodeSmell possibleCodeSmell, @NotNull ProblemsHolder holder) {
        if (possibleCodeSmell != null) {
            Map<PsiElement, String> refactoringMapping = possibleCodeSmell.getMappingFromPsiElementToRefactoring();
            for (PsiElement element : refactoringMapping.keySet()) {
                holder.registerProblem(element, possibleCodeSmell.getShortDescription(), new CodeSmellFix(possibleCodeSmell));
            }
        }
    }

}