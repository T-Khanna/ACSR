package visitors;

import codesmell.slowloop.SlowLoopCodeSmell;
import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;

public class IteratorVisitor extends JavaRecursiveElementWalkingVisitor {

    private final PsiLocalVariable referenceVariable;
    private final PsiLocalVariable iteratorVariable;

    private boolean tooManyNextCalls = false;
    private boolean iteratorIsInUse;

    private PsiMethodCallExpression nextCall = null;
    private PsiLocalVariable forEachReplacement = null;

    public IteratorVisitor(PsiLocalVariable referenceVariable, PsiLocalVariable iteratorVariable) {
        this.referenceVariable = referenceVariable;
        this.iteratorVariable = iteratorVariable;
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression methodCall) {
        PsiMethod method = methodCall.resolveMethod();
        if (method != null && methodCall.getArgumentList().isEmpty()) {
            String methodName = method.getName();
            PsiExpression qualifier = methodCall.getMethodExpression().getQualifierExpression();
            switch (methodName) {
                case HardcodedMethodConstants.NEXT:
                    if (isIteratorVariable(qualifier)) {
                        if (this.nextCall == null) {
                            this.nextCall = methodCall;
                        } else {
                            this.tooManyNextCalls = true;
                        }
                    }
                    break;
                case HardcodedMethodConstants.REMOVE:
                    if (isIteratorVariable(qualifier)) {
                        this.iteratorIsInUse = true;
                    }
                    break;
            }
        }
        super.visitMethodCallExpression(methodCall);
    }

    private boolean isIteratorVariable(PsiExpression qualifier) {
        return VariableAccessUtils.evaluatesToVariable(qualifier, this.iteratorVariable);
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (ExpressionUtils.isReferenceTo(expression, this.iteratorVariable)) {
            PsiElement grandParent = expression.getParent().getParent();
            if (grandParent instanceof PsiMethodCallExpression) {
                PsiMethodCallExpression methodCall = (PsiMethodCallExpression) grandParent;
                if (!HardcodedMethodConstants.NEXT.equals(methodCall.getMethodExpression().getReferenceName())) {
                    this.iteratorIsInUse = true;
                }
            }
        }
        super.visitReferenceExpression(expression);
    }

    @Override
    public void visitDeclarationStatement(PsiDeclarationStatement statement) {
        PsiElement[] elements =  statement.getDeclaredElements();
        for (PsiElement element : elements) {
            if (element instanceof PsiLocalVariable) {
                PsiLocalVariable declaredVariable = (PsiLocalVariable) element;
                PsiExpression initializer = declaredVariable.getInitializer();
                // Check if initializer is method call. If so, note the local variable to
                // pass onto the slow loop code smell constructor
                if (initializer instanceof PsiMethodCallExpression) {
                    PsiMethodCallExpression methodCallExp = (PsiMethodCallExpression) initializer;
                    PsiReferenceExpression methodExp = methodCallExp.getMethodExpression();
                    PsiMethod method = methodCallExp.resolveMethod();
                    PsiExpressionList args = methodCallExp.getArgumentList();
                    if (this.forEachReplacement == null && method != null && args.isEmpty()
                            && HardcodedMethodConstants.NEXT.equals(method.getName()) && isIteratorVariable(methodExp.getQualifierExpression())) {
                        this.forEachReplacement = declaredVariable;
                    }
                }
            }
        }

        super.visitDeclarationStatement(statement);
    }

    private boolean isSimpleForLoop() {
        return this.nextCall != null && !this.tooManyNextCalls && !this.iteratorIsInUse;
    }

    public SlowLoopCodeSmell getConstructedCodeSmell(PsiForStatement forStatement) {
        return isSimpleForLoop() ? new SlowLoopCodeSmell(forStatement, this.referenceVariable, this.nextCall, this.forEachReplacement) : null;
    }

}
