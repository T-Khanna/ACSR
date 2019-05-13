package visitors;

import codesmell.slowloop.*;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.psiutils.VariableAccessUtils;

public class IndexedLoopVisitor extends JavaRecursiveElementWalkingVisitor {

    private final PsiLocalVariable indexVariable;
    private final PsiVariable referenceVariable;

    private boolean isReassigned = false;
    private boolean isIndexOnlyUsedForReferenceAccess = true;
    private boolean isModified = false;

    private PsiLocalVariable forEachReplacement = null;
    private PsiExpression accessExpression = null;

    public IndexedLoopVisitor(PsiLocalVariable indexVariable, PsiVariable referenceVariable) {
        this.indexVariable = indexVariable;
        this.referenceVariable = referenceVariable;
    }

    @Override
    public void visitReferenceExpression(PsiReferenceExpression expression) {
        // Check if index variable is used in the expression. To determine if this is an instance of a
        // slow loop code smell, the index variable can ONLY be in one of these two cases:
        //   1. An array access expression, where the array is the reference variable
        //   2. A method call expression that is the list get method call
        if (isIndexVariable(expression)) {
            PsiElement parent = expression.getParent();
            if (parent instanceof PsiArrayAccessExpression) {
                PsiArrayAccessExpression arrayExp = (PsiArrayAccessExpression) parent;
                if (!isReferenceVariable(arrayExp.getArrayExpression())) {
                    this.isIndexOnlyUsedForReferenceAccess = false;
                } else if (this.accessExpression == null) {
                    this.accessExpression = arrayExp;
                }
            } else if (parent.getParent() instanceof PsiMethodCallExpression) {
                PsiMethodCallExpression methodCallExp = (PsiMethodCallExpression) parent.getParent();
                PsiReferenceExpression methodExp = methodCallExp.getMethodExpression();
                PsiMethod method = methodCallExp.resolveMethod();
                if (method != null) {
                    if (!HardcodedMethodConstants.GET.equals(method.getName()) || !isReferenceVariable(methodExp.getQualifierExpression())) {
                        this.isIndexOnlyUsedForReferenceAccess = false;
                    } else if (this.accessExpression == null) {
                        this.accessExpression = methodCallExp;
                    }
                }
            } else {
                this.isIndexOnlyUsedForReferenceAccess = false;
            }
        }
        super.visitReferenceExpression(expression);
    }

    @Override
    public void visitMethodCallExpression(PsiMethodCallExpression expression) {
        // Primarily check if the list is modified through remove() or clear() calls
        PsiReferenceExpression methodExp = expression.getMethodExpression();
        PsiMethod method = expression.resolveMethod();
        if (method != null) {
            String methodName = method.getName();
            if (isReferenceVariable(methodExp.getQualifierExpression())
                    && (methodName.contains(HardcodedMethodConstants.REMOVE) || methodName.equals("clear"))) {
                this.isModified = true;
            }
        }
        super.visitMethodCallExpression(expression);
    }

    @Override
    public void visitAssignmentExpression(PsiAssignmentExpression expression) {
        PsiExpression lhs = expression.getLExpression();
        if (lhs instanceof PsiArrayAccessExpression) {
            // In this case, the array is being modified by reassigning an element by index
            PsiArrayAccessExpression arrayExp = (PsiArrayAccessExpression) lhs;
            if (isReferenceVariable(arrayExp.getArrayExpression())) {
                this.isModified = true;
            }
        } else if (isReferenceVariable(lhs)) {
            // In this case, the reference variable is being reassigned, regardless of whether it is a list or array
            this.isReassigned = true;
        }

        super.visitAssignmentExpression(expression);
    }

    @Override
    public void visitDeclarationStatement(PsiDeclarationStatement statement) {
        PsiElement[] elements =  statement.getDeclaredElements();
        for (PsiElement element : elements) {
            if (element instanceof PsiLocalVariable) {
                PsiLocalVariable declaredVariable = (PsiLocalVariable) element;
                PsiExpression initializer = declaredVariable.getInitializer();
                // Check if initializer is array access or get method call. If so, note the local variable to
                // pass onto the slow loop code smell constructor
                if (initializer instanceof PsiArrayAccessExpression) {
                    PsiArrayAccessExpression arrayExp = (PsiArrayAccessExpression) initializer;
                    if (this.forEachReplacement == null && isReferenceVariable(arrayExp.getArrayExpression())
                            && isIndexVariable(arrayExp.getIndexExpression())) {
                        this.forEachReplacement = declaredVariable;
                    }
                } else if (initializer instanceof PsiMethodCallExpression) {
                    PsiMethodCallExpression methodCallExp = (PsiMethodCallExpression) initializer;
                    PsiReferenceExpression methodExp = methodCallExp.getMethodExpression();
                    PsiMethod method = methodCallExp.resolveMethod();
                    PsiExpressionList args = methodCallExp.getArgumentList();
                    if (method != null && args.getExpressionCount() == 1) {
                        PsiExpression possibleIndex = args.getExpressions()[0];
                        if (this.forEachReplacement == null && HardcodedMethodConstants.GET.equals(method.getName())
                                && isReferenceVariable(methodExp.getQualifierExpression()) && isIndexVariable(possibleIndex)) {
                            this.forEachReplacement = declaredVariable;
                        }
                    }
                }
            }
        }
        super.visitDeclarationStatement(statement);
    }

    private boolean isReferenceVariable(PsiExpression expression) {
        return VariableAccessUtils.evaluatesToVariable(expression, this.referenceVariable);
    }

    private boolean isIndexVariable(PsiExpression expression) {
        return VariableAccessUtils.evaluatesToVariable(expression, this.indexVariable);
    }

    private boolean isSimpleForLoop() {
        return this.accessExpression != null && !this.isModified && !this.isReassigned && this.isIndexOnlyUsedForReferenceAccess;
    }

    public SlowLoopCodeSmell getConstructedCodeSmell(PsiForStatement forStatement) {
        // If the for statement is not simple in the sense of only using the index for retrieving the item
        // at that position in the reference, the loop is not a Slow Loop code smell
        if (isSimpleForLoop()) {
            PsiType referenceVariableType = this.referenceVariable.getType();
            if (referenceVariableType instanceof PsiArrayType) {
                return new SlowLoopCodeSmell(forStatement, this.referenceVariable, this.accessExpression,  this.forEachReplacement);
            } else if (InheritanceUtil.isInheritor(referenceVariableType, CommonClassNames.JAVA_UTIL_LIST)) {
                PsiExpression listInitializer = this.referenceVariable.getInitializer();
                PsiType listVariableType = listInitializer == null ? referenceVariableType : listInitializer.getType();

                // "With an ArrayList, a hand-written counted loop is about 3x faster (with or without JIT)"
                // As such, double check the variable if its an ArrayList and ignore it if it is an instance of ArrayList
                if (InheritanceUtil.isInheritor(listVariableType, CommonClassNames.JAVA_UTIL_ARRAY_LIST)) {
                    return null;
                }

                return new SlowLoopCodeSmell(forStatement, this.referenceVariable, this.accessExpression, this.forEachReplacement);
            }
        }
        return null;
    }

}
