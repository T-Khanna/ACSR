package visitors;

import codesmell.CodeSmell;
import codesmell.slowloop.SlowLoopCodeSmell;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiForStatement;
import detection.DetectSlowLoop;

import java.util.LinkedHashSet;
import java.util.Set;

public class CodeVisitor extends JavaRecursiveElementWalkingVisitor {

    private final Set<CodeSmell> identifiedCodeSmells;

    public CodeVisitor() {
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

    public Set<CodeSmell> getIdentifiedCodeSmells() {
        return this.identifiedCodeSmells;
    }

}