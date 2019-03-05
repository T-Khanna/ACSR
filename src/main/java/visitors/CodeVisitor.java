package visitors;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

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
        if (isValidForEach(type)) {
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
                if (isValidForEach(type)) {
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
        if (isValidForEach(type)) {
            System.out.println(var.getText() + ", " + type);
        }
    }

    private boolean isValidForEach(PsiType type) {
        return type instanceof PsiArrayType ||
               type instanceof PsiClassType && isInstanceOfIterable((PsiClassType) type);
    }

    private boolean isInstanceOfIterable(PsiClassType classType) {
        PsiClass instance = classType.resolve();
        if (instance == null) {
            return false;
        }
        if (instance.isInterface()) {
            // Base case: Interface is Iterable
            if (classType.getClassName().equals("Iterable")) {
                return true;
            }
        } else {
            // Check if class implements an interface that is/extends Iterable
            for (PsiClassType implementType : instance.getImplementsListTypes()) {
                if (isInstanceOfIterable(implementType)) {
                    return true;
                }
            }
        }
        // Check for the following scenarios:
        // 1. The current interface extends an interface that is/extends Iterable
        // 2. The current class extends a class that implements an interface that is/extends Iterable
        for (PsiClassType extendType : instance.getExtendsListTypes()) {
            if (isInstanceOfIterable(extendType)) {
                return true;
            }
        }
        // At this point all possible checks have been done, and the type is not an instance
        // of Iterable, so return false here
        return false;
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