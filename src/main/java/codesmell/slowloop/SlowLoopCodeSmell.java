package codesmell.slowloop;

import codesmell.AbstractCodeSmell;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import utils.Constants;

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
    public String getAnnotationMessage() {
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
    public PsiElement getAssociatedPsiElement() {
        return this.forStatement;
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

    @Override
    public String getRefactoredCode() {
        StringBuilder result = new StringBuilder();
        result.append("for (");

        String variableType;
        String variableName;
        PsiDeclarationStatement declaration = null;
        if (this.forEachReplacement == null) {
            PsiType resolvedType = this.accessExpression.getType();
            if (resolvedType == null) {
                return null;
            }
            variableType = resolvedType.getPresentableText();
            variableName = variableType.substring(0, 1).toLowerCase();
        } else {
            variableType = this.forEachReplacement.getTypeElement().getText();
            variableName = this.forEachReplacement.getName();
            if (this.forEachReplacement.getParent() instanceof PsiDeclarationStatement) {
                declaration = (PsiDeclarationStatement) this.forEachReplacement.getParent();
            }
        }

        result.append(variableType);
        result.append(' ');
        result.append(variableName);
        result.append(" : ");
        result.append(this.referenceVariable.getName());
        result.append(')');

        PsiStatement body = this.forStatement.getBody();
        if (body instanceof PsiBlockStatement) {
            result.append(" {");
            PsiBlockStatement block = (PsiBlockStatement) body;
            for (PsiStatement statement : block.getCodeBlock().getStatements()) {
                if (statement.equals(declaration)) {
                    continue;
                }
                result.append(replaceAccessCallsInStat(statement, variableName));
            }
            result.append('}');
        } else if (body != null && !body.equals(declaration)) {
            result.append(replaceAccessCallsInStat(body, variableName));
        }

        return result.toString();
    }

    private String replaceAccessCallsInStat(PsiStatement statement, String variableName) {
        return statement.getText().replace(this.accessExpression.getText(), variableName);
    }

}