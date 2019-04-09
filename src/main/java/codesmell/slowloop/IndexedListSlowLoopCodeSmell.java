package codesmell.slowloop;

import com.intellij.psi.*;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import utils.RefactorSlowLoopUtils;

public class IndexedListSlowLoopCodeSmell extends SlowLoopCodeSmell {

    public IndexedListSlowLoopCodeSmell(PsiForStatement forStatement) {
        super(forStatement);
    }

    @Override
    public String getRefactoredCode() {
        final PsiVariable indexVariable = RefactorSlowLoopUtils.getIndexVariable(this.forStatement);
        if (indexVariable == null) {
            return null;
        }
        PsiExpression collectionSize = RefactorSlowLoopUtils.getMaximum(this.forStatement);
        if (collectionSize instanceof PsiReferenceExpression) {
            final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)collectionSize;
            final PsiElement target = referenceExpression.resolve();
            if (target instanceof PsiVariable) {
                final PsiVariable variable = (PsiVariable)target;
                collectionSize = ParenthesesUtils.stripParentheses(variable.getInitializer());
            }
        }
        if (!(collectionSize instanceof PsiMethodCallExpression)) {
            return null;
        }
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) ParenthesesUtils.stripParentheses(collectionSize);
        if (methodCallExpression == null) {
            return null;
        }
        final PsiReferenceExpression listLengthExpression = methodCallExpression.getMethodExpression();
        final PsiExpression qualifier = ParenthesesUtils.stripParentheses(ExpressionUtils.getQualifierOrThis(listLengthExpression));
        if (qualifier == null) {
            return null;
        }
        final PsiReferenceExpression listReference;
        if (qualifier instanceof PsiReferenceExpression) {
            listReference = (PsiReferenceExpression)qualifier;
        } else {
            listReference = null;
        }
        final PsiType type = qualifier.getType();
        if (type == null) {
            return null;
        }
        PsiType parameterType = RefactorSlowLoopUtils.getContentType(type, CommonClassNames.JAVA_UTIL_COLLECTION);
        if (parameterType == null) {
            parameterType = TypeUtils.getObjectType(this.forStatement);
        }
        final PsiVariable listVariable;
        if (listReference == null) {
            listVariable = null;
        } else {
            final PsiElement target = listReference.resolve();
            if (!(target instanceof PsiVariable)) {
                return null;
            }
            listVariable = (PsiVariable)target;
        }
        final PsiStatement body = this.forStatement.getBody();
        final PsiStatement firstStatement = RefactorSlowLoopUtils.getFirstStatement(body);
        final boolean isDeclaration = RefactorSlowLoopUtils.isListElementDeclaration(firstStatement, listVariable, indexVariable);
        final String contentVariableName;
        final String finalString;
        final PsiStatement statementToSkip;
        if (isDeclaration) {
            final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)firstStatement;
            final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
            final PsiElement declaredElement = declaredElements[0];
            if (!(declaredElement instanceof PsiVariable)) {
                return null;
            }
            final PsiVariable variable = (PsiVariable)declaredElement;
            contentVariableName = variable.getName();
            parameterType = variable.getType();
            statementToSkip = declarationStatement;
            if (variable.hasModifierProperty(PsiModifier.FINAL)) {
                finalString = "final ";
            } else {
                finalString = "";
            }
        } else {
            final String collectionName;
            if (listReference == null) {
                collectionName = null;
            } else {
                collectionName = listReference.getReferenceName();
            }
            contentVariableName = RefactorSlowLoopUtils.createNewVariableName(this.forStatement, parameterType, collectionName);
            finalString = "";
            statementToSkip = null;
        }
        final StringBuilder newStatement = new StringBuilder("for(");
        newStatement.append(finalString).append(parameterType.getCanonicalText()).append(' ').append(contentVariableName).append(": ");
        final String listName;
        if (listReference == null) {
            listName = this.commentTracker.text(qualifier);
        } else {
            listName = RefactorSlowLoopUtils.getVariableReferenceText(listReference, listVariable, this.forStatement);
        }
        newStatement.append(listName).append(')');
        if (body != null) {
            RefactorSlowLoopUtils.replaceCollectionGetAccess(body, contentVariableName, listVariable, indexVariable, statementToSkip, this.commentTracker, newStatement);
        }
        return newStatement.toString();
    }

}