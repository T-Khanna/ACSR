package codesmell.slowloop;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import utils.RefactorSlowLoopUtils;

public class ArraySlowLoopCodeSmell extends SlowLoopCodeSmell {

    public ArraySlowLoopCodeSmell(PsiForStatement forStatement) {
        super(forStatement);
    }

    @Override
    public String getRefactoredCode() {
        final PsiExpression maximum = RefactorSlowLoopUtils.getMaximum(this.forStatement);
        if (!(maximum instanceof PsiReferenceExpression)) {
            return null;
        }
        final PsiVariable indexVariable = RefactorSlowLoopUtils.getIndexVariable(this.forStatement);
        if (indexVariable == null) {
            return null;
        }
        final PsiReferenceExpression arrayLengthExpression = (PsiReferenceExpression)maximum;
        PsiReferenceExpression arrayReference = (PsiReferenceExpression) ParenthesesUtils.stripParentheses(arrayLengthExpression.getQualifierExpression());
        if (arrayReference == null) {
            final PsiElement target = arrayLengthExpression.resolve();
            if (!(target instanceof PsiVariable)) {
                return null;
            }
            final PsiVariable variable = (PsiVariable)target;
            final PsiExpression initializer = ParenthesesUtils.stripParentheses(variable.getInitializer());
            if (!(initializer instanceof PsiReferenceExpression)) {
                return null;
            }
            final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)initializer;
            arrayReference = (PsiReferenceExpression) ParenthesesUtils.stripParentheses(referenceExpression.getQualifierExpression());
            if (arrayReference == null) {
                return null;
            }
        }
        final PsiType type = arrayReference.getType();
        if (!(type instanceof PsiArrayType)) {
            return null;
        }
        final PsiArrayType arrayType = (PsiArrayType)type;
        PsiType componentType = arrayType.getComponentType();
        final PsiElement target = arrayReference.resolve();
        if (!(target instanceof PsiVariable)) {
            return null;
        }
        final PsiVariable arrayVariable = (PsiVariable)target;
        final PsiStatement body = forStatement.getBody();
        final PsiStatement firstStatement = RefactorSlowLoopUtils.getFirstStatement(body);
        final boolean isDeclaration = RefactorSlowLoopUtils.isArrayElementDeclaration(firstStatement, arrayVariable, indexVariable);
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
            componentType = variable.getType();
            if (VariableAccessUtils.variableIsAssigned(variable, forStatement)) {
                final String collectionName = arrayReference.getReferenceName();
                contentVariableName = RefactorSlowLoopUtils.createNewVariableName(forStatement, componentType, collectionName);
                if (JavaCodeStyleSettings.getInstance(forStatement.getContainingFile()).GENERATE_FINAL_LOCALS) {
                    finalString = "final ";
                } else {
                    finalString = "";
                }
                statementToSkip = null;
            } else {
                contentVariableName = variable.getName();
                statementToSkip = declarationStatement;
                if (variable.hasModifierProperty(PsiModifier.FINAL)) {
                    finalString = "final ";
                } else {
                    finalString = "";
                }
            }
        } else {
            final String collectionName = arrayReference.getReferenceName();
            contentVariableName = RefactorSlowLoopUtils.createNewVariableName(forStatement, componentType, collectionName);
            if (JavaCodeStyleSettings.getInstance(forStatement.getContainingFile()).GENERATE_FINAL_LOCALS) {
                finalString = "final ";
            } else {
                finalString = "";
            }
            statementToSkip = null;
        }
        final StringBuilder newStatement = new StringBuilder();
        newStatement.append("for(");
        newStatement.append(finalString);
        newStatement.append(componentType.getCanonicalText());
        newStatement.append(' ');
        newStatement.append(contentVariableName);
        newStatement.append(": ");
        final String arrayName = RefactorSlowLoopUtils.getVariableReferenceText(arrayReference, arrayVariable, forStatement);
        newStatement.append(arrayName);
        newStatement.append(')');
        if (body != null) {
            RefactorSlowLoopUtils.replaceArrayAccess(body, contentVariableName, arrayVariable, indexVariable, statementToSkip, this.commentTracker, newStatement);
        }
        return newStatement.toString();
    }

}