package codesmell.slowloop;

import codesmell.AbstractCodeSmell;
import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import utils.Constants;
import visitors.VariableNameVisitor;

import java.util.*;

public class SlowLoopCodeSmell extends AbstractCodeSmell {

    private final PsiForStatement forStatement;
    private final PsiVariable referenceVariable;
    private final PsiExpression accessExpression;
    private final PsiLocalVariable forEachReplacement;

    public SlowLoopCodeSmell(PsiForStatement forStatement, PsiVariable referenceVariable, PsiExpression accessExpression, PsiLocalVariable forEachReplacement) {
        super();
        this.forStatement = forStatement;
        this.referenceVariable = referenceVariable;
        this.accessExpression = accessExpression;
        this.forEachReplacement = forEachReplacement;
    }

    @Override
    public String getShortDescription() {
        return "Possible instance of Slow Loop code smell";
    }

    @Override
    public String getInformativeMessage(PsiFile psiFile) {
        int lineNum = getLineNum(psiFile, this.forStatement);
        StringBuilder sb = new StringBuilder();
        sb.append("<a href=\"");
        sb.append(Constants.NAVIGATE_TRIGGER);
        sb.append("\">");
        sb.append("For loop at line");
        sb.append(' ');
        sb.append(lineNum);
        sb.append("</a>");
        sb.append(' ');
        sb.append("is an instance of a Slow Loop code smell.");
        sb.append('\n');
        sb.append("As per the <a href=\"");
        sb.append(Constants.PERF_TIPS_URL);
        sb.append("\">official documentation</a>, it is recommended to use for-each syntax instead.");
        sb.append('\n');
        sb.append("Click <a href=\"");
        sb.append(Constants.REFACTOR_TRIGGER);
        sb.append("\">here</a> to refactor this code smell.");
        return sb.toString();
    }

    @Override
    protected void updateRefactorings() {
        this.refactoringMappings.put(this.forStatement, getRefactoredCode());
    }

    @Override
    public String toString() {
        return this.forStatement.getText();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SlowLoopCodeSmell)) {
            return false;
        }
        SlowLoopCodeSmell that = (SlowLoopCodeSmell) obj;
        String thisText = this.forStatement.getText().replaceAll("\\s+", "");
        String thatText = that.forStatement.getText().replaceAll("\\s+", "");
        return thisText.hashCode() == thatText.hashCode() && thisText.equals(thatText);
    }

    @Override
    public int hashCode() {
        String thisText = this.forStatement.getText().replaceAll("\\s+", "");
        return thisText.hashCode();
    }

    private String getRefactoredCode() {
        StringBuilder result = new StringBuilder();
        result.append("for ");
        result.append(this.forStatement.getLParenth());

        String variableType;
        String variableName;
        PsiDeclarationStatement declaration = null;
        if (this.forEachReplacement == null) {
            PsiType resolvedType = this.accessExpression.getType();
            if (resolvedType == null) {
                return null;
            }
            variableType = resolvedType.getPresentableText();
            // Strange bug with getPresentableText() for PsiType, where for type "X<?>", it returns
            // "X<capture of ?>". Strip this out before continuing
            variableType = variableType.replace("capture of", "");
            PsiElement containingMethod = this.forStatement;
            while (containingMethod != null && !(containingMethod instanceof PsiMethod)) {
                containingMethod = containingMethod.getParent();
            }
            variableName = getUnusedVariableName(containingMethod, variableType);
        } else {
            variableType = this.forEachReplacement.getTypeElement().getText();
            variableName = this.forEachReplacement.getName();
            if (this.forEachReplacement.getParent() instanceof PsiDeclarationStatement) {
                declaration = (PsiDeclarationStatement) this.forEachReplacement.getParent();
            }
        }

        // Check for inner class field access to outer class, qualifying the field properly
        String referenceName = getQualifiedReferenceName(this.accessExpression, this.referenceVariable);

        result.append(variableType);
        result.append(' ');
        result.append(variableName);
        result.append(" : ");
        result.append(referenceName);
        result.append(this.forStatement.getRParenth());

        PsiStatement body = this.forStatement.getBody();
        if (body instanceof PsiBlockStatement) {
            result.append(" {");
            PsiBlockStatement block = (PsiBlockStatement) body;
            for (PsiStatement statement : block.getCodeBlock().getStatements()) {
                if (statement.equals(declaration)) {
                    continue;
                }
                result.append(replaceAccessCallsInStat(this.accessExpression, statement, variableName));
            }
            result.append('}');
        } else if (body != null && !body.equals(declaration)) {
            result.append(replaceAccessCallsInStat(this.accessExpression, body, variableName));
        }

        return result.toString();
    }

    private String getUnusedVariableName(PsiElement containingElement, String variableType) {
        String defaultName = "autoGeneratedVariable";
        if (!(containingElement instanceof PsiMethod)) {
            return defaultName;
        }
        PsiMethod containingMethod = (PsiMethod) containingElement;
        VariableNameVisitor variableNameVisitor = new VariableNameVisitor();
        containingMethod.accept(variableNameVisitor);
        Set<String> unavailableVariableNames = variableNameVisitor.getVariableNames();
        List<String> javaReservedWords = Arrays.asList("abstract", "assert", "boolean",
                "break", "byte", "case", "catch", "char", "class", "const",
                "continue", "default", "do", "double", "else", "extends", "false",
                "final", "finally", "float", "for", "goto", "if", "implements",
                "import", "instanceof", "int", "interface", "long", "native",
                "new", "null", "package", "private", "protected", "public",
                "return", "short", "static", "strictfp", "super", "switch",
                "synchronized", "this", "throw", "throws", "transient", "true",
                "try", "void", "volatile", "while");
        unavailableVariableNames.addAll(javaReservedWords);
        StringBuilder variableName = new StringBuilder();
        for (char c : variableType.toCharArray()) {
            if (!Character.isAlphabetic(c)) {
                continue;
            }
            variableName.append(Character.toLowerCase(c));
            String constructedName = variableName.toString();
            if (!unavailableVariableNames.contains(constructedName)) {
                return constructedName;
            }
        }
        for (int i = 0; i < 100;i++) {
            String nthDefaultVariable = defaultName + i;
            if (!unavailableVariableNames.contains(nthDefaultVariable)) {
                return nthDefaultVariable;
            }
        }
        return defaultName;
    }

    private String getQualifiedReferenceName(PsiExpression accessExoression, PsiVariable referenceVariable) {
        String defaultName = referenceVariable.getName();
        // Split between array and iterable accesses
        if (accessExoression instanceof PsiArrayAccessExpression) {
            PsiArrayAccessExpression arrayAccess = (PsiArrayAccessExpression) accessExoression;
            return arrayAccess.getArrayExpression().getText();
        }
        if (accessExoression instanceof PsiMethodCallExpression) {
            PsiMethodCallExpression methodCall = (PsiMethodCallExpression) accessExoression;
            PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
            PsiExpression qualifier = methodExpression.getQualifierExpression();
            String methodName = methodExpression.getReferenceName();
            // Split between iterator and list accesses. In the iterator case, we want to navigate to the declaration
            // of the iterator and use the initializer to get the qualified name
            if (HardcodedMethodConstants.NEXT.equals(methodName) && qualifier instanceof PsiReferenceExpression) {
                PsiReferenceExpression qualifierReference = (PsiReferenceExpression) qualifier;
                PsiElement resolvedQualifier = qualifierReference.resolve();
                if (!(resolvedQualifier instanceof PsiLocalVariable)) {
                    return defaultName;
                }
                PsiLocalVariable iteratorDeclaration = (PsiLocalVariable) resolvedQualifier;
                PsiExpression initializer = iteratorDeclaration.getInitializer();
                if (!(initializer instanceof PsiMethodCallExpression)) {
                    return defaultName;
                }
                PsiMethodCallExpression iteratorMethodCall = (PsiMethodCallExpression) initializer;
                PsiExpression iteratorQualifierExpression = iteratorMethodCall.getMethodExpression().getQualifierExpression();
                if (iteratorQualifierExpression != null) {
                    return iteratorQualifierExpression.getText();
                }
            } else if (HardcodedMethodConstants.GET.equals(methodName) && qualifier != null) {
                return qualifier.getText();
            }
        }
        // If all else fails, use the non-qualified name from the reference variable
        return defaultName;
    }

    private String replaceAccessCallsInStat(PsiExpression accessExpression, PsiStatement statement, String variableName) {
        return statement.getText().replace(accessExpression.getText(), variableName);
    }

}