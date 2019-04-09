package detection;

import codesmell.slowloop.ArraySlowLoopCodeSmell;
import codesmell.slowloop.CollectionSlowLoopCodeSmell;
import codesmell.slowloop.IndexedListSlowLoopCodeSmell;
import codesmell.slowloop.SlowLoopCodeSmell;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import utils.Holder;
import visitors.IteratorNextVisitor;
import visitors.VariableOnlyUsedAsIndexVisitor;
import visitors.VariableOnlyUsedAsListIndexVisitor;

public class DetectSlowLoop {

    private static final boolean IGNORE_UNTYPED_COLLECTIONS = false;

    public static SlowLoopCodeSmell checkForSlowLoop(PsiForStatement forStatement) {
        // Check if its a collection for loop statement
        if (isCollectionLoopStatement(forStatement)) {
            return new CollectionSlowLoopCodeSmell(forStatement);
        }
        // Check if its an indexed list for loop statement
        if (isIndexedForStatement(forStatement, false)) {
            return new IndexedListSlowLoopCodeSmell(forStatement);
        }
        // Check if its an array for loop statement
        if (isIndexedForStatement(forStatement, true)) {
            return new ArraySlowLoopCodeSmell(forStatement);
        }
        return null;
    }

    private static boolean isCollectionLoopStatement(PsiForStatement forStatement) {
        PsiStatement initialization = forStatement.getInitialization();
        PsiVariable iterableRef = getIterableVariable(initialization, IGNORE_UNTYPED_COLLECTIONS);
        if (iterableRef == null) {
            return false;
        }
        final PsiExpression condition = forStatement.getCondition();
        if (!isHasNext(condition, iterableRef)) {
            return false;
        }
        final PsiStatement update = forStatement.getUpdate();
        if (update != null && !(update instanceof PsiEmptyStatement)) {
            return false;
        }
        final PsiStatement body = forStatement.getBody();
        if (body == null) {
            return false;
        }
        return hasSimpleNextCall(iterableRef, body);
    }


    private static boolean isIndexedForStatement(PsiForStatement forStatement, boolean isArray) {
        final PsiStatement initialization = forStatement.getInitialization();
        if (!(initialization instanceof PsiDeclarationStatement)) {
            return false;
        }
        final PsiDeclarationStatement declaration = (PsiDeclarationStatement)initialization;
        final PsiElement[] declaredElements = declaration.getDeclaredElements();
        final PsiElement secondDeclaredElement;
        if (declaredElements.length == 1) {
            secondDeclaredElement = null;
        } else if (declaredElements.length == 2) {
            secondDeclaredElement = declaredElements[1];
        } else {
            return false;
        }
        final PsiElement declaredElement = declaredElements[0];
        if (!(declaredElement instanceof PsiVariable)) {
            return false;
        }
        final PsiVariable indexVariable = (PsiVariable)declaredElement;
        final PsiExpression initialValue = indexVariable.getInitializer();
        final Object constant = ExpressionUtils.computeConstantExpression(initialValue);
        if (!(constant instanceof Integer)) {
            return false;
        }
        final int initialIndexValue = (Integer) constant;
        if (initialIndexValue != 0) {
            return false;
        }

        if (isArray) {
            return isArrayLoopStatement(forStatement, indexVariable, secondDeclaredElement);
        } else {
            return isIndexedListLoopStatement(forStatement, indexVariable, secondDeclaredElement);
        }
    }

    private static boolean isIndexedListLoopStatement(PsiForStatement forStatement, PsiVariable indexVariable, PsiElement secondDeclaredElement) {
        final PsiExpression condition = forStatement.getCondition();
        final Holder collectionHolder = getCollectionFromSizeComparison(condition, indexVariable, secondDeclaredElement);
        if (collectionHolder == null) {
            return false;
        }
        final PsiStatement update = forStatement.getUpdate();
        if (!VariableAccessUtils.variableIsIncremented(indexVariable, update)) {
            return false;
        }
        final PsiStatement body = forStatement.getBody();
        if (!isIndexVariableOnlyUsedAsListIndex(collectionHolder, indexVariable, body)) {
            return false;
        }
        if (collectionHolder != Holder.DUMMY) {
            final PsiVariable collection = collectionHolder.getVariable();
            final PsiClassType collectionType = (PsiClassType)collection.getType();
            final PsiType[] parameters = collectionType.getParameters();
            if (IGNORE_UNTYPED_COLLECTIONS && parameters.length == 0) {
                return false;
            }
            return !VariableAccessUtils.variableIsAssigned(collection, body);
        }
        return true;
    }

    private static boolean isArrayLoopStatement(PsiForStatement forStatement, PsiVariable indexVariable, PsiElement secondDeclaredElement) {
        final PsiStatement update = forStatement.getUpdate();
        if (!VariableAccessUtils.variableIsIncremented(indexVariable, update)) {
            return false;
        }
        final PsiExpression condition = forStatement.getCondition();
        final PsiReferenceExpression arrayReference = getVarRefFromCond(condition, indexVariable, secondDeclaredElement);
        if (arrayReference == null) {
            return false;
        }
        if (!(arrayReference.getType() instanceof PsiArrayType)) {
            return false;
        }
        final PsiElement element = arrayReference.resolve();
        if (!(element instanceof PsiVariable)) {
            return false;
        }
        final PsiVariable arrayVariable = (PsiVariable)element;
        final PsiStatement body = forStatement.getBody();
        return body == null ||
                isIndexVariableOnlyUsedAsIndex(arrayVariable, indexVariable, body) &&
                        !VariableAccessUtils.variableIsAssigned(arrayVariable, body) &&
                        !VariableAccessUtils.arrayContentsAreAssigned(arrayVariable, body);
    }

    private static Holder getCollectionFromSizeComparison(PsiExpression condition, PsiVariable variable, PsiElement secondDeclaredElement) {
        condition = ParenthesesUtils.stripParentheses(condition);
        if (!(condition instanceof PsiBinaryExpression)) {
            return null;
        }
        final PsiBinaryExpression binaryExpression = (PsiBinaryExpression)condition;
        final IElementType tokenType = binaryExpression.getOperationTokenType();
        final PsiExpression rhs = binaryExpression.getROperand();
        final PsiExpression lhs = binaryExpression.getLOperand();
        if (tokenType.equals(JavaTokenType.LT)) {
            if (!ExpressionUtils.isReferenceTo(lhs, variable)) {
                return null;
            }
            return getCollectionFromListMethodCall(rhs, HardcodedMethodConstants.SIZE, secondDeclaredElement);
        }
        if (tokenType.equals(JavaTokenType.GT)) {
            if (!ExpressionUtils.isReferenceTo(rhs, variable)) {
                return null;
            }
            return getCollectionFromListMethodCall(lhs, HardcodedMethodConstants.SIZE, secondDeclaredElement);
        }
        return null;
    }

    private static Holder getCollectionFromListMethodCall(PsiExpression expression, String methodName, PsiElement secondDeclaredElement) {
        expression = ParenthesesUtils.stripParentheses(expression);
        if (expression instanceof PsiReferenceExpression) {
            final PsiReferenceExpression referenceExpression = (PsiReferenceExpression) expression;
            final PsiElement target = referenceExpression.resolve();
            if (secondDeclaredElement != null && !secondDeclaredElement.equals(target)) {
                return null;
            }
            if (!(target instanceof PsiVariable)) {
                return null;
            }
            final PsiVariable variable = (PsiVariable)target;
            final PsiCodeBlock context = PsiTreeUtil.getParentOfType(variable, PsiCodeBlock.class);
            if (context == null) {
                return null;
            }
            if (VariableAccessUtils.variableIsAssigned(variable, context)) {
                return null;
            }
            expression = ParenthesesUtils.stripParentheses(variable.getInitializer());
        } else if (secondDeclaredElement !=  null) {
            return null;
        }
        if (!(expression instanceof PsiMethodCallExpression)) {
            return null;
        }
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression)expression;
        final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        final String referenceName = methodExpression.getReferenceName();
        if (!methodName.equals(referenceName)) {
            return null;
        }
        final PsiMethod method = methodCallExpression.resolveMethod();
        if (method == null) {
            return null;
        }
        final PsiClass containingClass = method.getContainingClass();
        if (!InheritanceUtil.isInheritor(containingClass, CommonClassNames.JAVA_UTIL_LIST)) {
            return null;
        }
        final PsiExpression qualifierExpression = ParenthesesUtils.stripParentheses(methodExpression.getQualifierExpression());
        if (qualifierExpression == null ||
                qualifierExpression instanceof PsiThisExpression ||
                qualifierExpression instanceof PsiSuperExpression) {
            return Holder.DUMMY;
        }
        if (!(qualifierExpression instanceof PsiReferenceExpression)) {
            return null;
        }
        final PsiReferenceExpression referenceExpression =
                (PsiReferenceExpression)qualifierExpression;
        final PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiVariable)) {
            return null;
        }
        final PsiVariable variable = (PsiVariable)target;
        return new Holder(variable);
    }

    private static PsiVariable getIterableVariable(PsiStatement statement, boolean ignoreUntypedCollections) {
        if (!(statement instanceof PsiDeclarationStatement)) {
            return null;
        }
        final PsiDeclarationStatement declaration = (PsiDeclarationStatement)statement;
        final PsiElement[] declaredElements = declaration.getDeclaredElements();
        if (declaredElements.length != 1) {
            return null;
        }
        final PsiElement declaredElement = declaredElements[0];
        if (!(declaredElement instanceof PsiVariable)) {
            return null;
        }
        final PsiVariable variable = (PsiVariable)declaredElement;
        if (!TypeUtils.variableHasTypeOrSubtype(variable, CommonClassNames.JAVA_UTIL_ITERATOR, "java.util.ListIterator")) {
            return null;
        }
        final PsiExpression initialValue = ParenthesesUtils.stripParentheses(variable.getInitializer());
        if (!(initialValue instanceof PsiMethodCallExpression)) {
            return null;
        }
        final PsiMethodCallExpression initialCall = (PsiMethodCallExpression)initialValue;
        if (!initialCall.getArgumentList().isEmpty()) {
            return null;
        }
        final PsiReferenceExpression initialMethodExpression = initialCall.getMethodExpression();
        final String initialCallName = initialMethodExpression.getReferenceName();
        if (!HardcodedMethodConstants.ITERATOR.equals(initialCallName) && !"listIterator".equals(initialCallName)) {
            return null;
        }
        final PsiExpression qualifier = ExpressionUtils.getQualifierOrThis(initialMethodExpression);
        if (qualifier instanceof PsiSuperExpression) {
            return null;
        }
        final PsiType qualifierType = qualifier.getType();
        if (!(qualifierType instanceof PsiClassType)) {
            return null;
        }
        final PsiClassType classType = (PsiClassType)qualifierType;
        final PsiClass qualifierClass = classType.resolve();
        if (ignoreUntypedCollections) {
            final PsiClassType type = (PsiClassType)variable.getType();
            final PsiType[] parameters = type.getParameters();
            final PsiType[] parameters1 = classType.getParameters();
            if (parameters.length == 0 && parameters1.length == 0) {
                return null;
            }
        }
        if (!InheritanceUtil.isInheritor(qualifierClass, CommonClassNames.JAVA_LANG_ITERABLE)) {
            return null;
        }
        return variable;
    }

    private static boolean hasSimpleNextCall(PsiVariable iterator, PsiElement context) {
        if (context == null) {
            return false;
        }
        final IteratorNextVisitor visitor = new IteratorNextVisitor(iterator);
        context.accept(visitor);
        return visitor.hasSimpleNextCall();
    }

    private static boolean isHasNext(PsiExpression condition, PsiVariable iterator) {
        condition = ParenthesesUtils.stripParentheses(condition);
        if (!(condition instanceof PsiMethodCallExpression)) {
            return false;
        }
        PsiMethodCallExpression call = (PsiMethodCallExpression)condition;
        if (!call.getArgumentList().isEmpty()) {
            return false;
        }
        PsiReferenceExpression methodExpression = call.getMethodExpression();
        String methodName = methodExpression.getReferenceName();
        if (!HardcodedMethodConstants.HAS_NEXT.equals(methodName)) {
            return false;
        }
        PsiExpression qualifier = methodExpression.getQualifierExpression();
        return ExpressionUtils.isReferenceTo(qualifier, iterator);
    }

    private static boolean isIndexVariableOnlyUsedAsIndex(PsiVariable arrayVariable, PsiVariable indexVariable, PsiStatement body) {
        if (body == null) {
            return true;
        }
        final VariableOnlyUsedAsIndexVisitor visitor = new VariableOnlyUsedAsIndexVisitor(arrayVariable, indexVariable);
        body.accept(visitor);
        return visitor.isIndexVariableUsedOnlyAsIndex();
    }

    private static boolean isIndexVariableOnlyUsedAsListIndex(Holder collectionHolder, PsiVariable indexVariable, PsiStatement body) {
        if (body == null) {
            return true;
        }
        final VariableOnlyUsedAsListIndexVisitor visitor = new VariableOnlyUsedAsListIndexVisitor(collectionHolder, indexVariable);
        body.accept(visitor);
        return visitor.isIndexVariableUsedOnlyAsIndex();
    }

    private static PsiReferenceExpression getVarRefFromCond(PsiExpression condition, PsiVariable indexVar, PsiElement sndDeclaredElement) {
        final PsiBinaryExpression binaryExpression = ObjectUtils.tryCast(ParenthesesUtils.stripParentheses(condition), PsiBinaryExpression.class);
        if (binaryExpression == null) {
            return null;
        }
        final IElementType tokenType = binaryExpression.getOperationTokenType();
        final PsiExpression lhs = ParenthesesUtils.stripParentheses(binaryExpression.getLOperand());
        final PsiExpression rhs = ParenthesesUtils.stripParentheses(binaryExpression.getROperand());
        if (lhs == null || rhs == null) {
            return null;
        }
        PsiReferenceExpression referenceExpression;
        if (tokenType.equals(JavaTokenType.LT)) {
            if (!ExpressionUtils.isReferenceTo(lhs, indexVar) || !(rhs instanceof PsiReferenceExpression)) {
                return null;
            }
            referenceExpression = (PsiReferenceExpression) rhs;
        } else if (tokenType.equals(JavaTokenType.GT)) {
            if (!ExpressionUtils.isReferenceTo(rhs, indexVar) || !(lhs instanceof PsiReferenceExpression)) {
                return null;
            }
            referenceExpression = (PsiReferenceExpression)lhs;
        } else {
            return null;
        }
        if (ExpressionUtils.getArrayFromLengthExpression(referenceExpression) == null) {
            final PsiElement target = referenceExpression.resolve();
            if (sndDeclaredElement != null && !sndDeclaredElement.equals(target)) {
                return null;
            }
            if (target instanceof PsiVariable) {
                final PsiVariable maxVariable = (PsiVariable)target;
                final PsiCodeBlock context = PsiTreeUtil.getParentOfType(maxVariable, PsiCodeBlock.class);
                if (context == null || VariableAccessUtils.variableIsAssigned(maxVariable, context)) return null;
                final PsiExpression expression = ParenthesesUtils.stripParentheses(maxVariable.getInitializer());
                if (!(expression instanceof PsiReferenceExpression)) {
                    return null;
                }
                referenceExpression = (PsiReferenceExpression)expression;
                if (ExpressionUtils.getArrayFromLengthExpression(referenceExpression) == null) {
                    return null;
                }
            }
        } else if (sndDeclaredElement != null) {
            return null;
        }
        final PsiExpression qualifierExpression = ParenthesesUtils.stripParentheses(referenceExpression.getQualifierExpression());
        if (qualifierExpression instanceof PsiReferenceExpression) {
            return (PsiReferenceExpression) qualifierExpression;
        } else if (qualifierExpression instanceof PsiThisExpression ||
                   qualifierExpression instanceof PsiSuperExpression ||
                   qualifierExpression == null) {
            return referenceExpression;
        }
        return null;
    }

    public static boolean isValidForEachType(PsiType type) {
        return type instanceof PsiArrayType ||
               type instanceof PsiClassType && isInstanceOfIterable((PsiClassType) type);
    }

    private static boolean isInstanceOfIterable(PsiClassType classType) {
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

}