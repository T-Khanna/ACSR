package refactoring;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.ig.psiutils.*;
import detection.DetectSlowLoop;
import utils.VariableNameGenerator;

public class RefactorSlowLoop {

    public static void refactorSlowLoop(PsiForStatement forStatement, Project project) {
        boolean ignoreUntypedCollections = false;
        if (DetectSlowLoop.isArrayLoopStatement(forStatement)) {
            replaceArrayLoopWithForeach(forStatement, project);
        } else if (DetectSlowLoop.isCollectionLoopStatement(forStatement, ignoreUntypedCollections)) {
            replaceCollectionLoopWithForeach(forStatement, project);
        } else if (DetectSlowLoop.isIndexedListLoopStatement(forStatement, ignoreUntypedCollections)) {
            replaceIndexedListLoopWithForeach(forStatement, project);
        }
    }

    private static void replaceIndexedListLoopWithForeach(PsiForStatement forStatement, Project project) {
        final PsiVariable indexVariable = getIndexVariable(forStatement);
        if (indexVariable == null) {
            return;
        }
        PsiExpression collectionSize = getMaximum(forStatement);
        if (collectionSize instanceof PsiReferenceExpression) {
            final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)collectionSize;
            final PsiElement target = referenceExpression.resolve();
            if (target instanceof PsiVariable) {
                final PsiVariable variable = (PsiVariable)target;
                collectionSize = ParenthesesUtils.stripParentheses(variable.getInitializer());
            }
        }
        if (!(collectionSize instanceof PsiMethodCallExpression)) {
            return;
        }
        final PsiMethodCallExpression methodCallExpression = (PsiMethodCallExpression) ParenthesesUtils.stripParentheses(collectionSize);
        if (methodCallExpression == null) {
            return;
        }
        final PsiReferenceExpression listLengthExpression = methodCallExpression.getMethodExpression();
        final PsiExpression qualifier = ParenthesesUtils.stripParentheses(ExpressionUtils.getQualifierOrThis(listLengthExpression));
        if (qualifier == null) {
            return;
        }
        final PsiReferenceExpression listReference;
        if (qualifier instanceof PsiReferenceExpression) {
            listReference = (PsiReferenceExpression)qualifier;
        }
        else {
            listReference = null;
        }
        final PsiType type = qualifier.getType();
        if (type == null) {
            return;
        }
        PsiType parameterType = getContentType(type, CommonClassNames.JAVA_UTIL_COLLECTION);
        if (parameterType == null) {
            parameterType = TypeUtils.getObjectType(forStatement);
        }
        final PsiVariable listVariable;
        if (listReference == null) {
            listVariable = null;
        }
        else {
            final PsiElement target = listReference.resolve();
            if (!(target instanceof PsiVariable)) {
                return;
            }
            listVariable = (PsiVariable)target;
        }
        final PsiStatement body = forStatement.getBody();
        final PsiStatement firstStatement = getFirstStatement(body);
        final boolean isDeclaration = isListElementDeclaration(firstStatement, listVariable, indexVariable);
        final String contentVariableName;
        final String finalString;
        final PsiStatement statementToSkip;
        if (isDeclaration) {
            final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)firstStatement;
            final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
            final PsiElement declaredElement = declaredElements[0];
            if (!(declaredElement instanceof PsiVariable)) {
                return;
            }
            final PsiVariable variable = (PsiVariable)declaredElement;
            contentVariableName = variable.getName();
            parameterType = variable.getType();
            statementToSkip = declarationStatement;
            if (variable.hasModifierProperty(PsiModifier.FINAL)) {
                finalString = "final ";
            }
            else {
                finalString = "";
            }
        }
        else {
            final String collectionName;
            if (listReference == null) {
                collectionName = null;
            }
            else {
                collectionName = listReference.getReferenceName();
            }
            contentVariableName = createNewVariableName(forStatement, parameterType, collectionName);
            finalString = "";
            statementToSkip = null;
        }
        final CommentTracker ct = new CommentTracker();
        final StringBuilder newStatement = new StringBuilder("for(");
        newStatement.append(finalString).append(parameterType.getCanonicalText()).append(' ').append(contentVariableName).append(": ");
        final String listName;
        if (listReference == null) {
            listName = ct.text(qualifier);
        }
        else {
            listName = getVariableReferenceText(listReference, listVariable, forStatement);
        }
        newStatement.append(listName).append(')');
        if (body != null) {
            replaceCollectionGetAccess(body, contentVariableName, listVariable, indexVariable, statementToSkip, ct, newStatement);
        }
        updateStatement(forStatement, project, ct, newStatement);
    }

    private static void replaceCollectionLoopWithForeach(PsiForStatement forStatement, Project project) {
        final PsiStatement body = forStatement.getBody();
        final PsiStatement firstStatement = getFirstStatement(body);
        final PsiStatement initialization = forStatement.getInitialization();
        if (!(initialization instanceof PsiDeclarationStatement)) {
            return;
        }
        final PsiDeclarationStatement declaration = (PsiDeclarationStatement)initialization;
        final PsiElement declaredIterator = declaration.getDeclaredElements()[0];
        if (!(declaredIterator instanceof PsiVariable)) {
            return;
        }
        final PsiVariable iterator = (PsiVariable)declaredIterator;
        final PsiMethodCallExpression initializer = (PsiMethodCallExpression) ParenthesesUtils.stripParentheses(iterator.getInitializer());
        if (initializer == null) {
            return;
        }
        final PsiReferenceExpression methodExpression = initializer.getMethodExpression();
        final PsiExpression collection = ParenthesesUtils.stripParentheses(ExpressionUtils.getQualifierOrThis(methodExpression));
        if (collection == null) {
            return;
        }
        final PsiType collectionType = collection.getType();
        if (collectionType == null) {
            return;
        }
        final PsiType contentType = getContentType(collectionType, CommonClassNames.JAVA_LANG_ITERABLE);
        if (contentType == null) {
            return;
        }
        PsiType iteratorContentType = getContentType(iterator.getType(), CommonClassNames.JAVA_UTIL_ITERATOR);
        if (TypeUtils.isJavaLangObject(iteratorContentType)) {
            iteratorContentType = getContentType(initializer.getType(), CommonClassNames.JAVA_UTIL_ITERATOR);
        }
        if (iteratorContentType == null) {
            return;
        }

        final boolean isDeclaration = isIteratorNextDeclaration(firstStatement, iterator, contentType);
        final PsiStatement statementToSkip;
        final String finalString;
        final String contentVariableName;
        if (isDeclaration) {
            final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)firstStatement;
            final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
            final PsiElement declaredElement = declaredElements[0];
            if (!(declaredElement instanceof PsiVariable)) {
                return;
            }
            final PsiVariable variable = (PsiVariable)declaredElement;
            iteratorContentType = variable.getType();
            contentVariableName = variable.getName();
            statementToSkip = declarationStatement;
            finalString = variable.hasModifierProperty(PsiModifier.FINAL) ? "final " : "";
        }
        else {
            if (collection instanceof PsiReferenceExpression) {
                final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)collection;
                final String collectionName = referenceExpression.getReferenceName();
                contentVariableName = createNewVariableName(forStatement, contentType, collectionName);
            }
            else {
                contentVariableName = createNewVariableName(forStatement, contentType, null);
            }
            finalString = JavaCodeStyleSettings.getInstance(forStatement.getContainingFile()).GENERATE_FINAL_LOCALS ? "final " : "";
            statementToSkip = null;
        }
        final String contentTypeString = iteratorContentType.getCanonicalText();
        final CommentTracker ct = new CommentTracker();
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
        newStatement.append(ct.text(collection));
        newStatement.append(')');
        replaceIteratorNext(body, contentVariableName, iterator, contentType, statementToSkip, ct, newStatement);
        updateStatement(forStatement, project, ct, newStatement);
    }

    private static void replaceArrayLoopWithForeach(PsiForStatement forStatement, Project project) {
        final PsiExpression maximum = getMaximum(forStatement);
        if (!(maximum instanceof PsiReferenceExpression)) {
            return;
        }
        final PsiVariable indexVariable = getIndexVariable(forStatement);
        if (indexVariable == null) {
            return;
        }
        final PsiReferenceExpression arrayLengthExpression = (PsiReferenceExpression)maximum;
        PsiReferenceExpression arrayReference = (PsiReferenceExpression) ParenthesesUtils.stripParentheses(arrayLengthExpression.getQualifierExpression());
        if (arrayReference == null) {
            final PsiElement target = arrayLengthExpression.resolve();
            if (!(target instanceof PsiVariable)) {
                return;
            }
            final PsiVariable variable = (PsiVariable)target;
            final PsiExpression initializer = ParenthesesUtils.stripParentheses(variable.getInitializer());
            if (!(initializer instanceof PsiReferenceExpression)) {
                return;
            }
            final PsiReferenceExpression referenceExpression = (PsiReferenceExpression)initializer;
            arrayReference = (PsiReferenceExpression) ParenthesesUtils.stripParentheses(referenceExpression.getQualifierExpression());
            if (arrayReference == null) {
                return;
            }
        }
        final PsiType type = arrayReference.getType();
        if (!(type instanceof PsiArrayType)) {
            return;
        }
        final PsiArrayType arrayType = (PsiArrayType)type;
        PsiType componentType = arrayType.getComponentType();
        final PsiElement target = arrayReference.resolve();
        if (!(target instanceof PsiVariable)) {
            return;
        }
        final PsiVariable arrayVariable = (PsiVariable)target;
        final PsiStatement body = forStatement.getBody();
        final PsiStatement firstStatement = getFirstStatement(body);
        final boolean isDeclaration = isArrayElementDeclaration(firstStatement, arrayVariable, indexVariable);
        final String contentVariableName;
        final String finalString;
        final PsiStatement statementToSkip;
        if (isDeclaration) {
            final PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)firstStatement;
            final PsiElement[] declaredElements = declarationStatement.getDeclaredElements();
            final PsiElement declaredElement = declaredElements[0];
            if (!(declaredElement instanceof PsiVariable)) {
                return;
            }
            final PsiVariable variable = (PsiVariable)declaredElement;
            componentType = variable.getType();
            if (VariableAccessUtils.variableIsAssigned(variable, forStatement)) {
                final String collectionName = arrayReference.getReferenceName();
                contentVariableName = createNewVariableName(forStatement, componentType, collectionName);
                if (JavaCodeStyleSettings.getInstance(forStatement.getContainingFile()).GENERATE_FINAL_LOCALS) {
                    finalString = "final ";
                }
                else {
                    finalString = "";
                }
                statementToSkip = null;
            }
            else {
                contentVariableName = variable.getName();
                statementToSkip = declarationStatement;
                if (variable.hasModifierProperty(PsiModifier.FINAL)) {
                    finalString = "final ";
                }
                else {
                    finalString = "";
                }
            }
        }
        else {
            final String collectionName = arrayReference.getReferenceName();
            contentVariableName = createNewVariableName(forStatement, componentType, collectionName);
            if (JavaCodeStyleSettings.getInstance(forStatement.getContainingFile()).GENERATE_FINAL_LOCALS) {
                finalString = "final ";
            }
            else {
                finalString = "";
            }
            statementToSkip = null;
        }
        final CommentTracker ct = new CommentTracker();
        final StringBuilder newStatement = new StringBuilder();
        newStatement.append("for(");
        newStatement.append(finalString);
        newStatement.append(componentType.getCanonicalText());
        newStatement.append(' ');
        newStatement.append(contentVariableName);
        newStatement.append(": ");
        final String arrayName = getVariableReferenceText(arrayReference, arrayVariable, forStatement);
        newStatement.append(arrayName);
        newStatement.append(')');
        if (body != null) {
            replaceArrayAccess(body, contentVariableName, arrayVariable, indexVariable, statementToSkip, ct, newStatement);
        }
        updateStatement(forStatement, project, ct, newStatement);
    }

    private static void updateStatement(PsiForStatement forStatement, Project project, CommentTracker ct, StringBuilder newStatement) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            ct.replaceAndRestoreComments(forStatement, newStatement.toString());
        });
    }

    private static  String createNewVariableName(PsiElement scope, PsiType type, String containerName) {
        return new VariableNameGenerator(scope, VariableKind.PARAMETER).byCollectionName(containerName).byType(type)
                .byName("value", "item", "element").generate(true);
    }

    private static PsiStatement getFirstStatement(PsiStatement body) {
        if (!(body instanceof PsiBlockStatement)) {
            return body;
        }
        final PsiBlockStatement block = (PsiBlockStatement)body;
        final PsiCodeBlock codeBlock = block.getCodeBlock();
        return ControlFlowUtils.getFirstStatementInBlock(codeBlock);
    }

    private static PsiType getContentType(PsiType type, String containerClassName) {
        PsiType parameterType = PsiUtil.substituteTypeParameter(type, containerClassName, 0, true);
        return GenericsUtil.getVariableTypeByExpressionType(parameterType);
    }

    private static PsiExpression getMaximum(PsiForStatement statement) {
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
        }
        else if (JavaTokenType.GT.equals(tokenType)) {
            return lhs;
        }
        else {
            return null;
        }
    }

    private static PsiVariable getIndexVariable(PsiForStatement forStatement) {
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

    private static void replaceArrayAccess(
            PsiElement element, String contentVariableName,
            PsiVariable arrayVariable, PsiVariable indexVariable,
            PsiElement childToSkip, CommentTracker commentTracker, StringBuilder out) {
        if (element instanceof PsiExpression && isArrayLookup((PsiExpression)element, indexVariable, arrayVariable)) {
            out.append(contentVariableName);
        }
        else {
            final PsiElement[] children = element.getChildren();
            if (children.length == 0) {
                if (PsiUtil.isJavaToken(element, JavaTokenType.INSTANCEOF_KEYWORD) && out.charAt(out.length() - 1) != ' ') {
                    out.append(' ');
                }
                out.append(commentTracker.text(element));
            }
            else {
                boolean skippingWhiteSpace = false;
                for (final PsiElement child : children) {
                    if (child.equals(childToSkip)) {
                        skippingWhiteSpace = true;
                    }
                    else if (child instanceof PsiWhiteSpace && skippingWhiteSpace) {
                        //don't do anything
                    }
                    else {
                        skippingWhiteSpace = false;
                        replaceArrayAccess(child, contentVariableName, arrayVariable, indexVariable, childToSkip, commentTracker, out);
                    }
                }
            }
        }
    }

    private static boolean isListElementDeclaration(PsiStatement statement, PsiVariable listVariable,
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

    private static String getVariableReferenceText(PsiReferenceExpression reference, PsiVariable variable, PsiElement context) {
        final String text = reference.getText();
        final PsiResolveHelper resolveHelper = PsiResolveHelper.SERVICE.getInstance(context.getProject());
        PsiExpression qualifier = reference.getQualifierExpression();
        while(qualifier != null) {
            if(!(qualifier instanceof PsiReferenceExpression)) return text;
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

    private static boolean isArrayElementDeclaration(PsiStatement statement, PsiVariable arrayVariable, PsiVariable indexVariable) {
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

    private static void replaceIteratorNext(PsiElement element, String contentVariableName,
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

    private static boolean isIteratorNextDeclaration(
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

    private static void replaceCollectionGetAccess(
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
