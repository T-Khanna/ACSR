package visitors;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CodeVisitor extends JavaRecursiveElementWalkingVisitor {

    private List<PsiElement> elementsToEvaluate;

    public CodeVisitor() {
        this.elementsToEvaluate = new ArrayList<>();
    }

    @Override
    public void visitForStatement(PsiForStatement statement) {
        PsiMethod methodCall = PsiTreeUtil.getParentOfType(statement, PsiMethod.class);
        PsiBlockStatement block = PsiTreeUtil.getChildOfType(statement, PsiBlockStatement.class);
        this.elementsToEvaluate.add(statement);
        PsiExpression cond = statement.getCondition();
        if (cond != null) {

        }
        super.visitForStatement(statement);
    }

    public List<PsiElement> getFlaggedElements() {
        return this.elementsToEvaluate;
    }

}