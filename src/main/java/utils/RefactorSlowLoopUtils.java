package utils;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;

public class RefactorSlowLoopUtils {

    public static  String createNewVariableName(PsiElement scope, PsiType type, String containerName) {
        return new VariableNameGenerator(scope, VariableKind.PARAMETER).byCollectionName(containerName).byType(type)
                .byName("value", "item", "element").generate(true);
    }

    public static PsiStatement getFirstStatement(PsiStatement body) {
        if (!(body instanceof PsiBlockStatement)) {
            return body;
        }
        final PsiBlockStatement block = (PsiBlockStatement)body;
        final PsiCodeBlock codeBlock = block.getCodeBlock();
        return ControlFlowUtils.getFirstStatementInBlock(codeBlock);
    }

    public static PsiType getContentType(PsiType type, String containerClassName) {
        PsiType parameterType = PsiUtil.substituteTypeParameter(type, containerClassName, 0, true);
        return GenericsUtil.getVariableTypeByExpressionType(parameterType);
    }

    public static PsiExpression getMaximum(PsiForStatement statement) {
        final PsiExpression condition = ParenthesesUtils.stripParentheses(statement.getCondition());
        if (!(condition instanceof PsiBinaryExpression)) {
            return null;
        }
        PsiBinaryExpression binaryExpression = (PsiBinaryExpression)condition;
        final PsiExpression lhs = ParenthesesUtils.stripParentheses(binaryExpression.getLOperand());
        if (lhs == null) {
            return null;
        }
        final PsiExpression rhs = ParenthesesUtils.stripParentheses(binaryExpression.getROperand());
        if (rhs == null) {
            return null;
        }
        final IElementType tokenType = binaryExpression.getOperationTokenType();
        if (JavaTokenType.LT.equals(tokenType)) {
            return rhs;
        } else if (JavaTokenType.GT.equals(tokenType)) {
            return lhs;
        } else {
            return null;
        }
    }

    public static PsiVariable getIndexVariable(PsiForStatement forStatement) {
        PsiStatement initialization = forStatement.getInitialization();
        if (!(initialization instanceof PsiDeclarationStatement)) {
            return null;
        }
        PsiElement declaration = ((PsiDeclarationStatement)initialization).getDeclaredElements()[0];
        if (!(declaration instanceof PsiVariable)) {
            return null;
        }
        return (PsiVariable) declaration;
    }

    public static void replaceArrayAccess(
            PsiElement element, String contentVariableName,
            PsiVariable arrayVariable, PsiVariable indexVariable,
            PsiElement childToSkip, CommentTracker commentTracker, StringBuilder out) {
        if (element instanceof PsiExpression && isArrayLookup((PsiExpression)element, indexVariable, arrayVariable)) {
            out.append(contentVariableName);
        } else {
            final PsiElement[] children = element.getChildren();
            if (children.length == 0) {
                if (PsiUtil.isJavaToken(element, JavaTokenType.INSTANCEOF_KEYWORD) && out.charAt(out.length() - 1) != ' ') {
                    out.append(' ');
                }
                out.append(commentTracker.text(element));
            } else {
                boolean skippingWhiteSpace = false;
                for (final PsiElement child : children) {
                    if (child.equals(childToSkip)) {
                        skippingWhiteSpace = true;
                    } else if (child instanceof PsiWhiteSpace && skippingWhiteSpace) {
                        // Don't do anything
                    } else {
                        skippingWhiteSpace = false;
                        replaceArrayAccess(child, contentVariableName, arrayVariable, indexVariable, childToSkip, commentTracker, out);
                    }
                }
            }
        }
    }

    public static boolean isListElementDeclaration(PsiStatement statement, PsiVariable listVariable,
                                             PsiVariable indexVariable) {
        if (!(statement instanceof PsiDeclarationStatement)) {
            return false;
        }
        final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)statement;
        final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
        if (declaredElements.length != 1) {
            return false;
        }
        final PsiElement declaredElement = declaredElements[0];
        if (!(declaredElement instanceof PsiVariable)) {
            return false;
        }
        final PsiVariable variable = (PsiVariable)declaredElement;
        final PsiExpression initializer = variable.getInitializer();
        return isListGetLookup(initializer, indexVariable, listVariable);
    }

    private static boolean isArrayLookup(PsiExpression expression, PsiVariable indexVariable, PsiVariable arrayVariable) {
        expression = ParenthesesUtils.stripParentheses(expression);
        if (!(expression instanceof PsiArrayAccessExpression)) {
            return false;
        }
        final PsiArrayAccessExpression arrayAccess = (PsiArrayAccessExpression) expression;
        final PsiExpression indexExpression = arrayAccess.getIndexExpression();
        if (!ExpressionUtils.isReferenceTo(indexExpression, indexVariable)) {
            return false;
        }
        final PsiExpression arrayExpression = ParenthesesUtils.stripParentheses(arrayAccess.getArrayExpression());
        if (!(arrayExpression instanceof PsiReferenceExpression)) {
            return false;
        }
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)arrayExpression;
        final PsiExpression qualifier = ParenthesesUtils.stripParentheses(referenceExpression.getQualifierExpression());
        if (qualifier != null && !(qualifier instanceof PsiThisExpression) && !(qualifier instanceof PsiSuperExpression)) {
            return false;
        }
        final PsiElement target = referenceExpression.resolve();
        return arrayVariable.equals(target);
    }

    public static String getVariableReferenceText(PsiReferenceExpression reference, PsiVariable variable, PsiElement context) {
        final String text = reference.getText();
        final PsiResolveHelper resolveHelper = PsiResolveHelper.SERVICE.getInstance(context.getProject());
        PsiExpression qualifier = reference.getQualifierExpression();
        while (qualifier != null) {
            if (!(qualifier instanceof PsiReferenceExpression)) {
                return text;
            }
            qualifier = ((PsiReferenceExpression)qualifier).getQualifierExpression();
        }
        final PsiVariable target = resolveHelper.resolveReferencedVariable(text, context);
        return variable != target ? ExpressionUtils.getQualifierOrThis(reference).getText() + "." + text : text;
    }

    public static boolean expressionIsListGetLookup(PsiExpression expression) {
        expression = ParenthesesUtils.stripParentheses(expression);
        if (!(expression instanceof PsiMethodCallExpression)) {
            return false;
        }
        final PsiMethodCallExpression reference = (PsiMethodCallExpression)expression;
        final PsiReferenceExpression methodExpression = reference.getMethodExpression();
        final PsiElement resolved = methodExpression.resolve();
        if (!(resolved instanceof PsiMethod)) {
            return false;
        }
        final PsiMethod method = (PsiMethod)resolved;
        if (!HardcodedMethodConstants.GET.equals(method.getName())) {
            return false;
        }
        final PsiClass aClass = method.getContainingClass();
        return InheritanceUtil.isInheritor(aClass, CommonClassNames.JAVA_UTIL_LIST);
    }

    private static boolean isListGetLookup(PsiElement element, PsiVariable indexVariable, PsiVariable listVariable) {
        if (!(element instanceof PsiExpression)) {
            return false;
        }
        final PsiExpression expression = (PsiExpression)element;
        if (!expressionIsListGetLookup(expression)) {
            return false;
        }
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) ParenthesesUtils.stripParentheses(expression);
        if (methodCallExpression == null) {
            return false;
        }
        final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        final PsiExpression qualifierExpression = ParenthesesUtils.stripParentheses(methodExpression.getQualifierExpression());

        final PsiExpressionList argumentList = methodCallExpression.getArgumentList();
        final PsiExpression[] expressions = argumentList.getExpressions();
        if (expressions.length != 1) {
            return false;
        }
        if (!ExpressionUtils.isReferenceTo(expressions[0], indexVariable)) {
            return false;
        }
        if (qualifierExpression == null ||
                qualifierExpression instanceof PsiThisExpression ||
                qualifierExpression instanceof PsiSuperExpression) {
            return listVariable == null;
        }
        if (!(qualifierExpression instanceof PsiReferenceExpression)) {
            return false;
        }
        final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)qualifierExpression;
        final PsiExpression qualifier = ParenthesesUtils.stripParentheses(referenceExpression.getQualifierExpression());
        if (qualifier != null && !(qualifier instanceof PsiThisExpression) && !(qualifier instanceof PsiSuperExpression)) {
            return false;
        }
        final PsiElement target = referenceExpression.resolve();
        return listVariable.equals(target);
    }

    public static boolean isArrayElementDeclaration(PsiStatement statement, PsiVariable arrayVariable, PsiVariable indexVariable) {
        if (!(statement instanceof PsiDeclarationStatement)) {
            return false;
        }
        final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)statement;
        final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
        if (declaredElements.length != 1) {
            return false;
        }
        final PsiElement declaredElement = declaredElements[0];
        if (!(declaredElement instanceof PsiVariable)) {
            return false;
        }
        final PsiVariable variable = (PsiVariable)declaredElement;
        final PsiExpression initializer = variable.getInitializer();
        return isArrayLookup(initializer, indexVariable, arrayVariable);
    }

    public static void replaceIteratorNext(PsiElement element, String contentVariableName,
                                    PsiVariable iterator, PsiType contentType, PsiElement childToSkip,
                                    CommentTracker commentTracker, StringBuilder out) {
        if (isIteratorNext(element, iterator, contentType)) {
            out.append(contentVariableName);
        } else {
            final PsiElement[] children = element.getChildren();
            if (children.length == 0) {
                if (PsiUtil.isJavaToken(element, JavaTokenType.INSTANCEOF_KEYWORD) && out.charAt(out.length() - 1) != ' ') {
                    out.append(' ');
                }
                out.append(commentTracker.text(element));
            } else {
                boolean skippingWhiteSpace = false;
                for (PsiElement child : children) {
                    if (shouldSkip(iterator, contentType, child) || child.equals(childToSkip)) {
                        skippingWhiteSpace = true;
                    } else if (!(child instanceof PsiWhiteSpace) || !skippingWhiteSpace) {
                        skippingWhiteSpace = false;
                        replaceIteratorNext(child, contentVariableName, iterator, contentType, childToSkip, commentTracker, out);
                    }
                }
            }
        }
    }

    private static boolean shouldSkip(PsiVariable iterator, PsiType contentType, PsiElement child) {
        if (!(child instanceof PsiExpressionStatement)) {
            return false;
        }
        final PsiExpressionStatement expressionStatement = (PsiExpressionStatement)child;
        final PsiExpression expression = expressionStatement.getExpression();
        return isIteratorNext(expression, iterator, contentType);
    }

    private static  boolean isIteratorNext(PsiElement element, PsiVariable iterator, PsiType contentType) {
        if (element == null) {
            return false;
        }
        if (element instanceof PsiParenthesizedExpression) {
            return isIteratorNext(((PsiParenthesizedExpression)element).getExpression(), iterator, contentType);
        }
        if (element instanceof PsiTypeCastExpression) {
            final PsiTypeCastExpression castExpression = (PsiTypeCastExpression)element;
            final PsiType type = castExpression.getType();
            if (!contentType.equals(type)) {
                return false;
            }
            final PsiExpression operand = castExpression.getOperand();
            return isIteratorNext(operand, iterator, contentType);
        }
        if (!(element instanceof PsiMethodCallExpression)) {
            return false;
        }
        final PsiMethodCallExpression callExpression = (PsiMethodCallExpression)element;
        if (!callExpression.getArgumentList().isEmpty()) {
            return false;
        }
        final PsiReferenceExpression reference = callExpression.getMethodExpression();
        final String referenceName = reference.getReferenceName();
        if (!HardcodedMethodConstants.NEXT.equals(referenceName)) {
            return false;
        }

        final PsiExpression expression = reference.getQualifierExpression();
        return ExpressionUtils.isReferenceTo(expression, iterator);
    }

    public static boolean isIteratorNextDeclaration(
            PsiStatement statement, PsiVariable iterator,
            PsiType contentType) {
        if (!(statement instanceof PsiDeclarationStatement)) {
            return false;
        }
        final PsiDeclarationStatement declarationStatement =
                (PsiDeclarationStatement)statement;
        final PsiElement[] declaredElements =
                declarationStatement.getDeclaredElements();
        if (declaredElements.length != 1) {
            return false;
        }
        final PsiElement declaredElement = declaredElements[0];
        if (!(declaredElement instanceof PsiVariable)) {
            return false;
        }
        final PsiVariable variable = (PsiVariable)declaredElement;
        final PsiExpression initializer = variable.getInitializer();
        return isIteratorNext(initializer, iterator, contentType);
    }

    public static void replaceCollectionGetAccess(
            PsiElement element, String contentVariableName,
            PsiVariable listVariable, PsiVariable indexVariable,
            PsiElement childToSkip, CommentTracker commentTracker,
            StringBuilder out) {
        if (isListGetLookup(element, indexVariable, listVariable)) {
            out.append(contentVariableName);
        } else {
            final PsiElement[] children = element.getChildren();
            if (children.length == 0) {
                if (PsiUtil.isJavaToken(element, JavaTokenType.INSTANCEOF_KEYWORD) && out.charAt(out.length() - 1) != ' ') {
                    out.append(' ');
                }
                out.append(commentTracker.text(element));
            } else {
                boolean skippingWhiteSpace = false;
                for (final PsiElement child : children) {
                    if (child.equals(childToSkip)) {
                        skippingWhiteSpace = true;
                    } else if (!(child instanceof PsiWhiteSpace) || !skippingWhiteSpace) {
                        skippingWhiteSpace = false;
                        replaceCollectionGetAccess(child, contentVariableName, listVariable, indexVariable, childToSkip, commentTracker, out);
                    }
                }
            }
        }
    }

}
