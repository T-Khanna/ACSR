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
    public PsiElement getAssociatedPsiElement() {
        return this.asyncTask;
    }

    @Override
    public String getRefactoredCode() {
        StringBuilder result = new StringBuilder();
        StringBuilder background = new StringBuilder();

        // Append class signature first
        PsiModifierList modifiers = this.asyncTask.getModifierList();
        PsiReferenceList extenders = this.asyncTask.getExtendsList();
        PsiReferenceList implementers = this.asyncTask.getImplementsList();
        String className = this.asyncTask.getName();
        if (modifiers != null) {
            result.append(modifiers.getText());
            result.append(' ');
        }
        result.append("class ");
        result.append(className);
        if (extenders != null) {
            result.append(' ');
            result.append(extenders.getText());
        }
        if (implementers != null) {
            result.append(' ');
            result.append(implementers.getText());
        }

        result.append(" {");

        // Append background method signature
        PsiType returnType = this.background.getReturnType();
        if (returnType == null) {
            return null;
        }
        appendSignature(this.background, background, returnType);
        background.append(" {");

        // Check onPreExecute() method
        background.append(extractHeavyTaskFromUIMethod(this.preExecute, result));

        // Append background content
        PsiCodeBlock body = this.background.getBody();
        if (body == null) {
            return null;
        }
        for (PsiStatement statement : body.getStatements()) {
            background.append(statement.getText());
        }

        // Check onProgressUpdate() method
        background.append(extractHeavyTaskFromUIMethod(this.progressUpdate, result));

        // Check onPostExecute() method
        background.append(extractHeavyTaskFromUIMethod(this.postExecute, result));

        background.append('}');
        result.append(background);

        for (PsiMethod method : this.asyncTask.getMethods()) {
            switch (method.getName()) {
                case "onPreExecute":
                case "onPostExecute":
                case "onProgressUpdate":
                case "doInBackground":
                    break;
                default:
                    result.append(method.getText());
            }
        }

        result.append('}');

        return result.toString();
    }

    private String extractHeavyTaskFromUIMethod(PsiMethod uiMethod, StringBuilder sb) {
        StringBuilder result = new StringBuilder();
        if (uiMethod == null) {
            return null;
        }

        // Append uiMethod signature
        PsiType returnType = uiMethod.getReturnType();
        if (returnType == null) {
            return null;
        }
        appendSignature(uiMethod, sb, returnType);
        sb.append(" {");

        PsiCodeBlock body = uiMethod.getBody();
        if (body == null) {
            return null;
        }
        for (PsiStatement statement : body.getStatements()) {
            String text = statement.getText();
            if (this.allStatementsToRemove.contains(statement)) {
                result.append(text);
            } else {
                sb.append(text);
            }
        }

        sb.append('}');
        return result.toString();
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
