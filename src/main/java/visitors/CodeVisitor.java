package visitors;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import utils.ForLoopUtils;

import java.util.ArrayList;
import java.util.List;

public class CodeVisitor extends JavaRecursiveElementWalkingVisitor {

    private final List<PsiElement> elementsToEvaluate;

    public CodeVisitor() {
        this.elementsToEvaluate = new ArrayList<>();
    }

    @Override
    public void visitField(PsiField field) {
        super.visitField(field);
        String name = field.getName();
        PsiType type = field.getType();
        if (ForLoopUtils.isValidForEachType(type)) {
            System.out.println(name + ", " + type);
        }
    }

    @Override
    public void visitDeclarationStatement(PsiDeclarationStatement statement) {
        super.visitDeclarationStatement(statement);
        for (PsiElement elem : statement.getDeclaredElements()) {
            if (elem instanceof PsiLocalVariable) {
                PsiLocalVariable var = (PsiLocalVariable) elem;
                PsiType type = var.getType();
                if (ForLoopUtils.isValidForEachType(type)) {
                    System.out.println(var.getName() + ", " + type);
                }
            }
        }
    }

    @Override
    public void visitAssignmentExpression(PsiAssignmentExpression expression) {
        super.visitAssignmentExpression(expression);
        PsiExpression var = expression.getLExpression();
        PsiType type = var.getType();
        if (ForLoopUtils.isValidForEachType(type)) {
            System.out.println(var.getText() + ", " + type);
        }
    }

    @Override
    public void visitForStatement(PsiForStatement forStatement) {
        PsiMethod methodCall = PsiTreeUtil.getParentOfType(forStatement, PsiMethod.class);
        PsiBlockStatement block = PsiTreeUtil.getChildOfType(forStatement, PsiBlockStatement.class);
        this.elementsToEvaluate.add(forStatement);
        super.visitForStatement(forStatement);
    }

    public List<PsiElement> getFlaggedElements() {
        return this.elementsToEvaluate;
    }

}