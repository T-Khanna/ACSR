package visitors;

import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;
import refactoring.RefactorSlowLoop;
import utils.Holder;

public class VariableOnlyUsedAsListIndexVisitor extends JavaRecursiveElementWalkingVisitor {

    private boolean indexVariableUsedOnlyAsIndex = true;
    private boolean listGetCalled;
    private final PsiVariable indexVariable;
    private final Holder collection;

    public VariableOnlyUsedAsListIndexVisitor(@NotNull Holder collection, @NotNull PsiVariable indexVariable) {
        this.collection = collection;
        this.indexVariable = indexVariable;
    }

    @Override
    public void visitElement(@NotNull PsiElement element) {
        if (indexVariableUsedOnlyAsIndex) {
            super.visitElement(element);
        }
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression reference) {
        if (!indexVariableUsedOnlyAsIndex) {
            return;
        }
        super.visitReferenceExpression(reference);
        final PsiElement element = reference.resolve();
        if (indexVariable.equals(element)) {
            if (!isListIndexExpression(reference)) {
                indexVariableUsedOnlyAsIndex = false;
            }
            else {
                listGetCalled = true;
            }
        }
        else if (collection == Holder.DUMMY) {
            if (isListNonGetMethodCall(reference)) {
                indexVariableUsedOnlyAsIndex = false;
            }
        }
        else if (collection.getVariable().equals(element) && !isListReferenceInIndexExpression(reference)) {
            indexVariableUsedOnlyAsIndex = false;
        }
    }

    public boolean isIndexVariableUsedOnlyAsIndex() {
        return indexVariableUsedOnlyAsIndex && listGetCalled;
    }

    private boolean isListNonGetMethodCall(PsiReferenceExpression reference) {
        final PsiElement parent = reference.getParent();
        if (!(parent instanceof PsiMethodCallExpression)) {
            return false;
        }
        final PsiMethodCallExpression methodCallExpression =
                (PsiMethodCallExpression)parent;
        final PsiMethod method =
                methodCallExpression.resolveMethod();
        if (method == null) {
            return false;
        }
        final PsiClass parentClass = PsiTreeUtil.getParentOfType(
                methodCallExpression, PsiClass.class);
        final PsiClass containingClass = method.getContainingClass();
        if (!InheritanceUtil.isInheritorOrSelf(parentClass,
                containingClass, true)) {
            return false;
        }
        return !isListGetExpression(methodCallExpression);
    }

    private boolean isListIndexExpression(PsiReferenceExpression reference) {
        final PsiElement referenceParent = ParenthesesUtils.getParentSkipParentheses(reference);
        if (!(referenceParent instanceof PsiExpressionList)) {
            return false;
        }
        final PsiExpressionList expressionList = (PsiExpressionList)referenceParent;
        final PsiElement parent = expressionList.getParent();
        if (!(parent instanceof PsiMethodCallExpression)) {
            return false;
        }
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)parent;
        return isListGetExpression(methodCallExpression);
    }

    private boolean isListReferenceInIndexExpression(PsiReferenceExpression reference) {
        final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(reference);
        if (!(parent instanceof PsiReferenceExpression)) {
            return false;
        }
        final PsiElement grandParent = parent.getParent();
        if (!(grandParent instanceof PsiMethodCallExpression)) {
            return false;
        }
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)grandParent;
        final PsiElement greatGrandParent = methodCallExpression.getParent();
        if (greatGrandParent instanceof PsiExpressionStatement) {
            return false;
        }
        return isListGetExpression(methodCallExpression);
    }

    private boolean isListGetExpression(PsiMethodCallExpression methodCallExpression) {
        if (methodCallExpression == null) {
            return false;
        }
        final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        final PsiExpression qualifierExpression = ParenthesesUtils.stripParentheses(methodExpression.getQualifierExpression());
        if (!(qualifierExpression instanceof PsiReferenceExpression)) {
            if (collection == Holder.DUMMY &&
                    (qualifierExpression == null ||
                            qualifierExpression instanceof PsiThisExpression ||
                            qualifierExpression instanceof PsiSuperExpression)) {
                return RefactorSlowLoop.expressionIsListGetLookup(methodCallExpression);
            }
            return false;
        }
        final PsiReferenceExpression reference = (PsiReferenceExpression) qualifierExpression;
        final PsiExpression qualifier = ParenthesesUtils.stripParentheses(reference.getQualifierExpression());
        if (qualifier != null && !(qualifier instanceof PsiThisExpression) && !(qualifier instanceof PsiSuperExpression)) {
            return false;
        }
        final PsiElement target = reference.resolve();
        if (collection == Holder.DUMMY || !collection.getVariable().equals(target)) {
            return false;
        }
        return RefactorSlowLoop.expressionIsListGetLookup(methodCallExpression);
    }


}
