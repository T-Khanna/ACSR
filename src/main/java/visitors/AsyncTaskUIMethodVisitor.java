package visitors;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class AsyncTaskUIMethodVisitor extends JavaRecursiveElementWalkingVisitor {

    private Set<PsiStatement> statementsToIgnore;
    private Set<PsiParameter> uiMethodParameters;

    public AsyncTaskUIMethodVisitor(PsiParameter[] parameters) {
        this.statementsToIgnore = new HashSet<>();
        this.uiMethodParameters = new HashSet<>();
        this.uiMethodParameters.addAll(Arrays.asList(parameters));
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression methodCallExp) {
        if (isUIType(methodCallExp.getType())) {
            // Ignore the variable declaration statements, where the variable is used in this method call
            PsiExpressionList args = methodCallExp.getArgumentList();
            for (PsiExpression expression : args.getExpressions()) {
                if (expression instanceof PsiReferenceExpression) {
                    PsiReferenceExpression referenceExp = (PsiReferenceExpression) expression;
                    PsiElement resolvedReference = referenceExp.resolve();
                    if (resolvedReference instanceof PsiVariable) {
                        addSurroundingStatementToIgnores(resolvedReference);
                    }
                }
            }
            // Also ignore the statement where the method call is being made
            addSurroundingStatementToIgnores(methodCallExp);
        }
        super.visitMethodCallExpression(methodCallExp);
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
        // If any statement uses an expression that resolves to a method parameter, mark the statement as ignored
        PsiElement element = expression.resolve();
        if (element instanceof PsiParameter && this.uiMethodParameters.contains(element)) {
            addSurroundingStatementToIgnores(expression);
        }
        super.visitReferenceExpression(expression);
    }

    @Override
    public void visitSuperExpression(PsiSuperExpression expression) {
        addSurroundingStatementToIgnores(expression);
        super.visitSuperExpression(expression);
    }

    @Override
    public void visitReturnStatement(PsiReturnStatement statement) {
        this.statementsToIgnore.add(statement);
        super.visitReturnStatement(statement);
    }

    private void addSurroundingStatementToIgnores(PsiElement element) {
        while (element != null && !(element instanceof PsiStatement)) {
            element = element.getParent();
        }
        if (element != null) {
            PsiStatement statement = (PsiStatement) element;
            this.statementsToIgnore.add(statement);
        }
    }

    private boolean isUIType(PsiType type) {
        PsiClass classOfType = PsiUtil.resolveClassInType(type);
        return InheritanceUtil.isInheritor(type, "android.view.View") ||
                classOfType != null && "android.widget".equals(PsiUtil.getPackageName(classOfType));
    }

    public Set<PsiStatement> getStatementsToIgnore() {
        return this.statementsToIgnore;
    }

}
