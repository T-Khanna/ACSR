package detection;

import codesmell.slowloop.SlowLoopCodeSmell;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.psiutils.*;
import visitors.IndexedLoopVisitor;
import visitors.IteratorVisitor;

public class DetectSlowLoop {


    /**
     * Need to check two cases mainly:
     * 1. Indexed for loop with array or List (consider ignoring ArrayList if possible)
     * 2. Iterator for loop
     * --------------------------------------------------------------------------------
     * In addition, consider a different approach compared to IntelliJ. The plan will be
     * to build up the components for the code smell, returning false/null if any component
     * cannot be built. From that point, pass those components into the constructor and use
     * them according to build the refactored code.
     *
     * @param forStatement the standard for loop to inspect for the Slow Loop code smell
     * @return the detected slow loop code smell, or null if detection fails
     */
    public static SlowLoopCodeSmell checkForSlowLoop(PsiForStatement forStatement) {
        PsiStatement forInitializer =  forStatement.getInitialization();
        if (!(forInitializer instanceof PsiDeclarationStatement)) {
            return null;
        }
        PsiDeclarationStatement initializer = (PsiDeclarationStatement) forInitializer;

        // Check we are only initialising one local variable in the for statement
        PsiElement[] declaredElements = initializer.getDeclaredElements();
        if (declaredElements.length != 1) {
            return null;
        }
        PsiElement declaration = declaredElements[0];
        if (!(declaration instanceof PsiLocalVariable)) {
            return null;
        }
        PsiLocalVariable declaredVariable = (PsiLocalVariable) declaration;

        // Split the checking for an iterator loop and an indexed loop
        if (TypeUtils.variableHasTypeOrSubtype(declaredVariable, CommonClassNames.JAVA_UTIL_ITERATOR)) {
            return checkForIteratorLoop(declaredVariable, forStatement);
        }
        PsiType declaredVariableType = declaredVariable.getType();
        if (declaredVariableType instanceof PsiPrimitiveType && declaredVariableType.equals(PsiType.INT)){
            return checkForIndexedLoop(declaredVariable, forStatement);
        }
        return null;
    }

    private static SlowLoopCodeSmell checkForIndexedLoop(PsiLocalVariable indexVariable, PsiForStatement forStatement) {
        // Check the declared variable starts at 0
        PsiExpression indexExpression = indexVariable.getInitializer();
        Object constant = ExpressionUtils.computeConstantExpression(indexExpression);
        if (!(constant instanceof Integer)) {
            return null;
        }
        int indexValue = (Integer) constant;
        if (indexValue != 0) {
            return null;
        }

        // Retrieve the reference variable, extracting it from the for loop condition
        PsiExpression condition = ParenthesesUtils.stripParentheses(forStatement.getCondition());
        if (!(condition instanceof PsiBinaryExpression)) {
            return null;
        }
        PsiBinaryExpression binaryExpression = (PsiBinaryExpression) condition;
        IElementType tokenType = binaryExpression.getOperationTokenType();
        PsiExpression lhs = ParenthesesUtils.stripParentheses(binaryExpression.getLOperand());
        PsiExpression rhs = ParenthesesUtils.stripParentheses(binaryExpression.getROperand());
        if (lhs == null || rhs == null) {
            return null;
        }
        PsiReferenceExpression referenceExpression = null;
        if (tokenType.equals(JavaTokenType.LT)) {
            referenceExpression = getVariableReference(lhs, indexVariable, rhs);
        } else if (tokenType.equals(JavaTokenType.GT)) {
            referenceExpression = getVariableReference(rhs, indexVariable, lhs);
        }
        if (referenceExpression == null) {
            return null;
        }
        PsiExpression qualifierExpression = referenceExpression.getQualifierExpression();
        if (!(qualifierExpression instanceof PsiReferenceExpression)) {
            return null;
        }
        PsiElement referenceElement = ((PsiReferenceExpression) qualifierExpression).resolve();
        if (!(referenceElement instanceof PsiVariable)) {
            return null;
        }
        PsiVariable referenceVariable = (PsiVariable) referenceElement;

        // Check if the index variable is incremented in the update statement in the for loop
        PsiStatement forUpdate = forStatement.getUpdate();
        if (!VariableAccessUtils.variableIsIncremented(indexVariable, forUpdate)) {
            return null;
        }

        // Check for modifications to the reference or a change in assignment to the variable
        // Also check if index variable is used only for retrieving the item from iterable in the for loop
        PsiStatement body = forStatement.getBody();
        if (body == null) {
            return null;
        }
        IndexedLoopVisitor indexedLoopVisitor = new IndexedLoopVisitor(indexVariable, referenceVariable);
        body.accept(indexedLoopVisitor);
        return indexedLoopVisitor.getConstructedCodeSmell(forStatement);
    }

    private static SlowLoopCodeSmell checkForIteratorLoop(PsiLocalVariable iteratorVariable, PsiForStatement forStatement) {
        PsiExpression initializer = iteratorVariable.getInitializer();
        // Check that initializer call is the iterator() method.
        if (!(initializer instanceof PsiMethodCallExpression)) {
            return null;
        }
        PsiMethodCallExpression initializerMethodCall = (PsiMethodCallExpression) initializer;
        PsiMethod initializerMethod = initializerMethodCall.resolveMethod();
        if (initializerMethod == null || !HardcodedMethodConstants.ITERATOR.equals(initializerMethod.getName())
                || !initializerMethodCall.getArgumentList().isEmpty()) {
            return null;
        }
        PsiExpression initializerQualifier = initializerMethodCall.getMethodExpression().getQualifierExpression();
        if (!(initializerQualifier instanceof PsiReferenceExpression)) {
            return null;
        }
        PsiReferenceExpression qualifierReference = (PsiReferenceExpression) initializerQualifier;
        PsiElement resolvedReference = qualifierReference.resolve();
        if (!(resolvedReference instanceof PsiVariable)) {
            return null;
        }
        PsiVariable referenceVariable = (PsiVariable) resolvedReference;

        // Check that condition is the hasNext() method call on the iterator variable
        PsiExpression condition = forStatement.getCondition();
        if (!(condition instanceof PsiMethodCallExpression)) {
            return null;
        }
        PsiMethodCallExpression conditionMethodCall = (PsiMethodCallExpression) condition;
        PsiMethod conditionMethod = conditionMethodCall.resolveMethod();
        if (conditionMethod == null || !HardcodedMethodConstants.HAS_NEXT.equals(conditionMethod.getName())
                || !conditionMethodCall.getArgumentList().isEmpty()) {
            return null;
        }
        PsiExpression conditionQualifier = conditionMethodCall.getMethodExpression().getQualifierExpression();
        if (!VariableAccessUtils.evaluatesToVariable(conditionQualifier, iteratorVariable)) {
            return null;
        }

        // Check that there is no update statement present in the for loop statement
        PsiStatement update = forStatement.getUpdate();
        if (update != null) {
            return null;
        }

        PsiStatement body = forStatement.getBody();
        if (body == null) {
            return null;
        }
        IteratorVisitor iteratorVisitor = new IteratorVisitor(referenceVariable, iteratorVariable);
        body.accept(iteratorVisitor);
        return iteratorVisitor.getConstructedCodeSmell(forStatement);
    }

    private static PsiReferenceExpression getVariableReference(PsiExpression indexExpression, PsiLocalVariable indexVariable, PsiExpression reference) {
        // Check if the expression on the left hand side of the binary expression is the index variable
        if (ExpressionUtils.isReferenceTo(indexExpression, indexVariable)) {
            if (reference instanceof PsiReferenceExpression) {
                // The reference is an array, so ensure than the reference is accessing the length field
                PsiReferenceExpression referenceExp = (PsiReferenceExpression) reference;
                if ("length".equals(referenceExp.getReferenceName())) {
                    return referenceExp;
                }
                PsiElement resolvedReference = referenceExp.resolve();
                if (resolvedReference instanceof PsiVariable) {
                    PsiVariable capacityVariable = (PsiVariable) resolvedReference;
                    return getVariableReference(indexExpression, indexVariable, capacityVariable.getInitializer());
                }
            } else if (reference instanceof PsiMethodCallExpression) {
                // The reference is a list, so check if the reference is called the size() method
                PsiReferenceExpression methodExp = ((PsiMethodCallExpression) reference).getMethodExpression();
                if (HardcodedMethodConstants.SIZE.equals(methodExp.getReferenceName())) {
                    return methodExp;
                }
            }
        }
        return null;
    }

}
