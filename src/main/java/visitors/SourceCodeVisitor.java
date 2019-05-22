package visitors;

import codesmell.CodeSmell;
import codesmell.heavyasynctask.HeavyAsyncTaskCodeSmell;
import codesmell.slowloop.SlowLoopCodeSmell;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiForStatement;
import detection.DetectHeavyAsyncTask;
import detection.DetectSlowLoop;

import java.util.LinkedHashSet;
import java.util.Set;

public class SourceCodeVisitor extends JavaRecursiveElementWalkingVisitor {

    private final Set<CodeSmell> identifiedCodeSmells;

    public SourceCodeVisitor() {
        this.identifiedCodeSmells = new LinkedHashSet<>();
    }

    @Override
    public void visitForStatement(PsiForStatement forStatement) {
        SlowLoopCodeSmell possibleSlowLoop = DetectSlowLoop.checkForSlowLoop(forStatement);
        if (possibleSlowLoop != null) {
            this.identifiedCodeSmells.add(possibleSlowLoop);
        }
        super.visitForStatement(forStatement);
    }

    @Override
    public void visitClass(PsiClass aClass) {
        HeavyAsyncTaskCodeSmell possibleHeavyAsyncTask = DetectHeavyAsyncTask.checkForHeavyAsyncTask(aClass);
        if (possibleHeavyAsyncTask != null) {
            this.identifiedCodeSmells.add(possibleHeavyAsyncTask);
        }
        super.visitClass(aClass);
    }

    public Set<CodeSmell> getIdentifiedCodeSmells() {
        return this.identifiedCodeSmells;
    }

}