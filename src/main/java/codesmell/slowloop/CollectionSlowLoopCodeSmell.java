package codesmell.slowloop;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import utils.RefactorSlowLoopUtils;

public class CollectionSlowLoopCodeSmell extends SlowLoopCodeSmell {

    public CollectionSlowLoopCodeSmell(PsiForStatement forStatement) {
        super(forStatement);
    }

    @Override
    public String getRefactoredCode() {
        final PsiStatement body = this.forStatement.getBody();
        final PsiStatement firstStatement = RefactorSlowLoopUtils.getFirstStatement(body);
        final PsiStatement initialization = this.forStatement.getInitialization();
        if (!(initialization instanceof PsiDeclarationStatement)) {
            return null;
        }
        final PsiDeclarationStatement declaration = (PsiDeclarationStatement)initialization;
        final PsiElement declaredIterator = declaration.getDeclaredElements()[0];
        if (!(declaredIterator instanceof PsiVariable)) {
            return null;
        }
        final PsiVariable iterator = (PsiVariable)declaredIterator;
        final PsiMethodCallExpression initializer = (PsiMethodCallExpression) ParenthesesUtils.stripParentheses(iterator.getInitializer());
        if (initializer == null) {
            return null;
        }
        final PsiReferenceExpression methodExpression = initializer.getMethodExpression();
        final PsiExpression collection = ParenthesesUtils.stripParentheses(ExpressionUtils.getQualifierOrThis(methodExpression));
        if (collection == null) {
            return null;
        }
        final PsiType collectionType = collection.getType();
        if (collectionType == null) {
            return null;
        }
        final PsiType contentType = RefactorSlowLoopUtils.getContentType(collectionType, CommonClassNames.JAVA_LANG_ITERABLE);
        if (contentType == null) {
            return null;
        }
        PsiType iteratorContentType = RefactorSlowLoopUtils.getContentType(iterator.getType(), CommonClassNames.JAVA_UTIL_ITERATOR);
        if (TypeUtils.isJavaLangObject(iteratorContentType)) {
            iteratorContentType = RefactorSlowLoopUtils.getContentType(initializer.getType(), CommonClassNames.JAVA_UTIL_ITERATOR);
        }
        if (iteratorContentType == null) {
            return null;
        }

        final boolean isDeclaration = RefactorSlowLoopUtils.isIteratorNextDeclaration(firstStatement, iterator, contentType);
        final PsiStatement statementToSkip;
        final String finalString;
        final String contentVariableName;
        if (isDeclaration) {
            final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)firstStatement;
            final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
            final PsiElement declaredElement = declaredElements[0];
            if (!(declaredElement instanceof PsiVariable)) {
                return null;
            }
            final PsiVariable variable = (PsiVariable)declaredElement;
            iteratorContentType = variable.getType();
            contentVariableName = variable.getName();
            statementToSkip = declarationStatement;
            finalString = variable.hasModifierProperty(PsiModifier.FINAL) ? "final " : "";
        } else {
            if (collection instanceof PsiReferenceExpression) {
                final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)collection;
                final String collectionName = referenceExpression.getReferenceName();
                contentVariableName = RefactorSlowLoopUtils.createNewVariableName(this.forStatement, contentType, collectionName);
            } else {
                contentVariableName = RefactorSlowLoopUtils.createNewVariableName(this.forStatement, contentType, null);
            }
            finalString = JavaCodeStyleSettings.getInstance(this.forStatement.getContainingFile()).GENERATE_FINAL_LOCALS ? "final " : "";
            statementToSkip = null;
        }
        final String contentTypeString = iteratorContentType.getCanonicalText();
        final StringBuilder newStatement = new StringBuilder();
        newStatement.append("for(");
        newStatement.append(finalString);
        newStatement.append(contentTypeString);
        newStatement.append(' ');
        newStatement.append(contentVariableName);
        newStatement.append(": ");
        if (!TypeConversionUtil.isAssignable(iteratorContentType, contentType)) {
            newStatement.append('(').append("java.lang.Iterable<").append(contentTypeString).append('>').append(')');
        }
        newStatement.append(this.commentTracker.text(collection));
        newStatement.append(')');
        RefactorSlowLoopUtils.replaceIteratorNext(body, contentVariableName, iterator, contentType, statementToSkip, this.commentTracker, newStatement);
        return newStatement.toString();
    }

}