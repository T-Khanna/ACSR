package visitors;

import codesmell.CodeSmell;
import codesmell.slowloop.SlowLoopCodeSmell;
import com.intellij.psi.*;
import detection.DetectSlowLoop;

import java.util.ArrayList;
import java.util.List;

public class CodeVisitor extends JavaRecursiveElementWalkingVisitor {

    private final List<CodeSmell> identifiedCodeSmells;

    public CodeVisitor() {
        this.identifiedCodeSmells = new ArrayList<>();
    }

    @Override
    public void visitForStatement(PsiForStatement forStatement) {
        SlowLoopCodeSmell possibleSlowLoop = DetectSlowLoop.checkForSlowLoop(forStatement);
        if (possibleSlowLoop != null) {
            this.identifiedCodeSmells.add(possibleSlowLoop);
        }
        super.visitForStatement(forStatement);
    }

    public List<CodeSmell> getIdentifiedCodeSmells() {
        return this.identifiedCodeSmells;
    }

}