package codesmell.heavyasynctask;

import codesmell.AbstractCodeSmell;
import com.intellij.psi.*;
import utils.Constants;

import java.util.Set;

public class HeavyAsyncTaskCodeSmell extends AbstractCodeSmell {

    private final PsiClass asyncTask;
    private final PsiMethod preExecute;
    private final PsiMethod progressUpdate;
    private final PsiMethod postExecute;
    private final Set<PsiStatement> allStatementsToRemove;
    private final PsiMethod background;

    public HeavyAsyncTaskCodeSmell(PsiClass asyncTask, PsiMethod background, PsiMethod preExecute, PsiMethod progressUpdate,
                                   PsiMethod postExecute, Set<PsiStatement> allStatementsToRemove) {
        super();
        this.asyncTask = asyncTask;
        this.background = background;
        this.preExecute = preExecute;
        this.progressUpdate = progressUpdate;
        this.postExecute = postExecute;
        this.allStatementsToRemove = allStatementsToRemove;
    }

    @Override
    public String getInformativeMessage(PsiFile psiFile) {
        int lineNum = getLineNum(psiFile, this.asyncTask);
        StringBuilder sb = new StringBuilder();
        sb.append("<a href=\"");
        sb.append(Constants.NAVIGATE_TRIGGER);
        sb.append("\">");
        sb.append("AsyncTask at line");
        sb.append(' ');
        sb.append(lineNum);
        sb.append("</a>");
        sb.append(' ');
        sb.append("is an instance of a Heavy AsyncTask (HAS) code smell.");
        sb.append('\n');
        sb.append("Click <a href=\"");
        sb.append(Constants.REFACTOR_TRIGGER);
        sb.append("\">here</a> to refactor this code smell.");
        return sb.toString();
    }

    @Override
    public String getShortDescription() {
        return "Possible Heavy AsyncTask code smell";
    }

    @Override
    protected void updateRefactorings() {
        StringBuilder background = new StringBuilder();

        // Append background method signature
        PsiType returnType = this.background.getReturnType();
        if (returnType == null) {
            return;
        }
        appendSignature(this.background, background, returnType);
        background.append(" {");

        // Check onPreExecute() method
        checkUIMethod(background, this.preExecute);

        // Append background content
        PsiCodeBlock body = this.background.getBody();
        if (body == null) {
            return;
        }

        PsiReturnStatement lastStat = null;

        PsiStatement[] statements = body.getStatements();
        int lastStatementIndex = statements.length - 1;
        for (int i = 0; i < lastStatementIndex; i++) {
            PsiStatement statement = statements[i];
            background.append(statement.getText());
        }

        // Check onProgressUpdate() method
        checkUIMethod(background, this.progressUpdate);
        // Check onPostExecute() method
        checkUIMethod(background, this.postExecute);

        background.append(statements[lastStatementIndex].getText());

        background.append('}');
        this.refactoringMappings.put(this.background, background.toString());
    }

    private void checkUIMethod(StringBuilder background, PsiMethod uiMethod) {
        if (uiMethod == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        StringBuilder result = new StringBuilder();

        // Append uiMethod signature
        PsiType returnType = uiMethod.getReturnType();
        if (returnType == null) {
            return;
        }
        appendSignature(uiMethod, sb, returnType);
        sb.append(" {");

        PsiCodeBlock body = uiMethod.getBody();
        if (body == null) {
            return;
        }
        for (PsiStatement statement : body.getStatements()) {
            String text = statement.getText();
            if (this.allStatementsToRemove.contains(statement)) {
                // Add statement to background, replacing void "return;" with Object "return null;"
                result.append(text.replace("return;", "return null;"));
            } else {
                sb.append(text);
            }
        }

        sb.append('}');
        background.append(result.toString());
        this.refactoringMappings.put(uiMethod, sb.toString());
    }

    private void appendSignature(PsiMethod method, StringBuilder sb, PsiType returnType) {
        PsiModifierList modifiers = method.getModifierList();
        PsiParameterList params = method.getParameterList();
        String methodName = method.getName();

        sb.append(modifiers.getText());
        sb.append(' ');
        sb.append(returnType.getPresentableText());
        sb.append(' ');
        sb.append(methodName);
        sb.append(params.getText());
    }
    
}
