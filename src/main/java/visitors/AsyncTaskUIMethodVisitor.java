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
        if (isUIMethodCall(methodCallExp)) {
            // Ignore the variable declaration statements, where the variable is used in this method call
            PsiExpressionList args = methodCallExp.getArgumentList();
            for (PsiExpression expression : args.getExpressions()) {
                checkForVariableReference(expression);
            }
            // Also ignore the statement where the method call is being made
            addSurroundingStatementToIgnores(methodCallExp);
        } else {
            // Check if the qualifier declaration is added to ignore list.
            // If so, include this expression in the ignore list.
            PsiReferenceExpression methodRef = methodCallExp.getMethodExpression();
            PsiExpression qualifier = methodRef.getQualifierExpression();
            if (qualifier instanceof PsiReferenceExpression) {
                PsiReferenceExpression qualifierRef = (PsiReferenceExpression) qualifier;
                PsiElement resolvedRef = qualifierRef.resolve();
                PsiStatement resolvedStat = getSurroundingStatement(resolvedRef);
                if (resolvedStat != null && this.statementsToIgnore.contains(resolvedStat)) {
                    addSurroundingStatementToIgnores(methodCallExp);
                }
            }
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
        addSurroundingStatementToIgnores(statement);
        super.visitReturnStatement(statement);
    }

    @Override
    public void visitAssignmentExpression(PsiAssignmentExpression expression) {
        PsiExpression lhs = expression.getLExpression();
        PsiExpression rhs = expression.getRExpression();
        if (rhs instanceof PsiMethodCallExpression && isUIMethodCall((PsiMethodCallExpression) rhs) ||
                rhs != null && isUIType(rhs.getType())) {
            addSurroundingStatementToIgnores(expression);
            checkForVariableReference(lhs);
        }
        super.visitAssignmentExpression(expression);
    }

    public Set<PsiStatement> getStatementsToIgnore() {
        return this.statementsToIgnore;
    }

    private void checkForVariableReference(PsiExpression possibleVariableRef) {
        if (possibleVariableRef instanceof PsiReferenceExpression) {
            PsiReferenceExpression ref = (PsiReferenceExpression) possibleVariableRef;
            PsiElement resolvedRef = ref.resolve();
            if (resolvedRef instanceof PsiVariable) {
                addSurroundingStatementToIgnores(resolvedRef);
            }
        }
    }

    private void addSurroundingStatementToIgnores(PsiElement element) {
        PsiStatement surroundingStatement = getSurroundingStatement(element);
        if (surroundingStatement != null) {
            this.statementsToIgnore.add(surroundingStatement);
        }
    }

    private PsiStatement getSurroundingStatement(PsiElement element) {
        while (element != null) {
            PsiElement parent = element.getParent();
            // Check if we have reached the fully encompassed statement
            if (parent instanceof PsiCodeBlock && parent.getParent() instanceof PsiMethod) {
                break;
            }
            element = parent;
        }
        if (element instanceof PsiStatement) {
            return (PsiStatement) element;
        }
        return null;
    }

    private boolean isUIMethodCall(PsiMethodCallExpression methodCallExp) {
        PsiReferenceExpression methodRef = methodCallExp.getMethodExpression();
        PsiExpression qualifier = methodRef.getQualifierExpression();
        PsiType methodCallType = methodCallExp.getType();

        Set<String> uiMethodNames = new HashSet<>();
        uiMethodNames.add("finish");

        return isUIType(methodCallType) ||
                qualifier != null && isUIType(qualifier.getType()) ||
                uiMethodNames.contains(methodRef.getReferenceName());
    }

    private boolean isUIType(PsiType type) {
        if (InheritanceUtil.isInheritor(type, "android.view.View")) {
            return true;
        }
        PsiClass classOfType = PsiUtil.resolveClassInType(type);
        if (classOfType != null) {
            String packageName = PsiUtil.getPackageName(classOfType);
            return "android.widget".equals(packageName) || "android.app".equals(packageName);
        }
        return false;
    }

}
