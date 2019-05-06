package codesmell.heavyasynctask;

import codesmell.AbstractCodeSmell;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;

public class HeavyAsyncTaskCodeSmell extends AbstractCodeSmell {

    private PsiClass asyncTask;

    public HeavyAsyncTaskCodeSmell(PsiClass asyncTask) {
        super();
        this.asyncTask = asyncTask;
    }

    @Override
    public String getInformativeMessage(PsiFile psiFile) {
        return null;
    }

    @Override
    public String getAnnotationMessage() {
        return null;
    }

    @Override
    public PsiElement getAssociatedPsiElement() {
        return this.asyncTask;
    }

    @Override
    public String getRefactoredCode() {
        // Need to rearrange heavy tasks from UI methods to doInBackground()

        /*
         * PsiMethod preExecute = PsiMethod.findMethod(this.asyncTask, "onPreExecute");
         * PsiMethod progressUpdate = PsiMethod.findMethod(this.asyncTask, "onProgressUpdate");
         * PsiMethod postExecute = PsiMethod.findMethod(this.asyncTask, "onPostExecute");
         *
         * PsiMethod background = PsiMethod.findMethod(this.asyncTask, "doInBackground");
         *
         * // If doInBackground() does not exist, create it with reasonable types.
         * if (background == null) {
         *     background = PsiMethod.createMethod("doInBackground");
         *     this.asyncTask.addMethod(background);
         * }
         *
         * background.addText(removeHeavyTaskFromPreExecute(preExecute);
         * background.addText(removeHeavyTaskFromPreExecute(progressUpdate);
         * background.addText(removeHeavyTaskFromPreExecute(postExecute);
         *
         */
        return null;
    }

    private String removeHeavyTaskFromPreExecute(PsiMethod onPreExecute) {
        // Remove heavy task in onPreExecute() method and return
        // the heavy task for use in doInBackground()
        return null;
    }

    private String removeHeavyTaskFromProgressUpdate(PsiMethod onProgressUpdate) {
        // Remove heavy task in onProgressUpdate() method and return
        // the heavy task for use in doInBackground()
        return null;
    }

    private String removeHeavyTaskFromPostExecute(PsiMethod onPostExecute) {
        // Remove heavy task in onPostExecute() method and return
        // the heavy task for use in doInBackground()
        return null;
    }

    private String extractNonUIStatements(PsiMethod method) {
        // Theory: Anything that extends a View, treat it as a UI related statement.
        //         Might need to include others like Dialog, etc.
        //         In addition, ignore super methods, and also delete any methods that become empty.
        return null;
    }

}
