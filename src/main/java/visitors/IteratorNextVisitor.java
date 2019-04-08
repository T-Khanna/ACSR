package visitors;

import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.NotNull;

public class IteratorNextVisitor extends JavaRecursiveElementWalkingVisitor {

    private int numCallsToIteratorNext = 0;
    private boolean iteratorUsed = false;
    private final PsiVariable iterator;

    public IteratorNextVisitor(PsiVariable iterator) {
        this.iterator = iterator;
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
        if (iteratorUsed || numCallsToIteratorNext > 1) {
            return;
        }
        if (expression.getArgumentList().isEmpty()) {
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if (HardcodedMethodConstants.NEXT.equals(methodName)) {
                final PsiExpression qualifier = methodExpression.getQualifierExpression();
                if (ExpressionUtils.isReferenceTo(qualifier, iterator)) {
                    numCallsToIteratorNext++;
                    return;
                }
            }
        }
        super.visitMethodCallExpression(expression);
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (iteratorUsed || numCallsToIteratorNext > 1) {
            return;
        }
        super.visitReferenceExpression(expression);
        if (ExpressionUtils.isReferenceTo(expression, iterator)) {
            iteratorUsed = true;
            stopWalking();
        }
    }

    public boolean hasSimpleNextCall() {
        return numCallsToIteratorNext == 1 && !iteratorUsed;
    }

}
