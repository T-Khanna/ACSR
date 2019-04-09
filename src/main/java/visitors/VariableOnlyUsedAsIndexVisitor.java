package visitors;

import com.intellij.psi.*;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

import static com.siyeh.ig.psiutils.ParenthesesUtils.stripParentheses;

public class VariableOnlyUsedAsIndexVisitor extends JavaRecursiveElementWalkingVisitor  {

    private boolean indexVariableUsedOnlyAsIndex = true;
    private boolean arrayAccessed = false;
    private final PsiVariable arrayVariable;
    private final PsiVariable indexVariable;

    public VariableOnlyUsedAsIndexVisitor(PsiVariable arrayVariable, PsiVariable indexVariable) {
        this.arrayVariable = arrayVariable;
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
        if (!indexVariable.equals(element)) {
            return;
        }
        final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(reference);
        if (!(parent instanceof PsiArrayAccessExpression)) {
            indexVariableUsedOnlyAsIndex = false;
            return;
        }
        final PsiArrayAccessExpression arrayAccessExpression = (PsiArrayAccessExpression)parent;
        final PsiExpression arrayExpression = stripParentheses(arrayAccessExpression.getArrayExpression());
        if (!(arrayExpression instanceof PsiReferenceExpression)) {
            indexVariableUsedOnlyAsIndex = false;
            return;
        }
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)arrayExpression;
        final PsiExpression qualifier = referenceExpression.getQualifierExpression();
        if (qualifier != null && !(qualifier instanceof PsiThisExpression) && !(qualifier instanceof PsiSuperExpression)) {
            indexVariableUsedOnlyAsIndex = false;
            return;
        }
        final PsiElement target = referenceExpression.resolve();
        if (!arrayVariable.equals(target)) {
            indexVariableUsedOnlyAsIndex = false;
            return;
        }
        arrayAccessed = true;
        final PsiElement arrayExpressionContext = ParenthesesUtils.getParentSkipParentheses(arrayAccessExpression);
        if (arrayExpressionContext instanceof PsiAssignmentExpression) {
            final PsiAssignmentExpression assignment = (PsiAssignmentExpression)arrayExpressionContext;
            final PsiExpression lhs = assignment.getLExpression();
            if (lhs.equals(arrayAccessExpression)) {
                indexVariableUsedOnlyAsIndex = false;
            }
        }
    }

    public boolean isIndexVariableUsedOnlyAsIndex() {
        return indexVariableUsedOnlyAsIndex && arrayAccessed;
    }

}