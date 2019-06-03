package detection;

import codesmell.heavyasynctask.HeavyAsyncTaskCodeSmell;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.PsiUtil;
import visitors.AsyncTaskUIMethodVisitor;

import java.util.*;

public class DetectHeavyAsyncTask {

    public static HeavyAsyncTaskCodeSmell checkForHeavyAsyncTask(PsiClass classDec) {
        // Check class declaration is of an AsyncTask

        // Need to check onPreExecute(), onProgressUpdate() and onPostExecute() methods
        String className = "android.os.AsyncTask";
        if (PsiUtil.isAbstractClass(classDec) || !InheritanceUtil.isInheritor(classDec, className)) {
            return null;
        }

        PsiMethod background = null;
        PsiMethod preExecute = null;
        PsiMethod progressUpdate = null;
        PsiMethod postExecute = null;
        Set<PsiMethod> heavyUiMethods = new HashSet<>();
        Set<PsiStatement> allStatementsToRemove = new LinkedHashSet<>();

        PsiMethod[] methods = classDec.getMethods();
        for (PsiMethod method : methods)  {
            switch (method.getName()) {
                case "doInBackground":
                    background = method;
                    break;
                case "onPreExecute":
                    updateUiMethods(heavyUiMethods, allStatementsToRemove, method);
                    if (heavyUiMethods.contains(method)) {
                        preExecute = method;
                    }
                    break;
                case "onProgressUpdate":
                    updateUiMethods(heavyUiMethods, allStatementsToRemove, method);
                    if (heavyUiMethods.contains(method)) {
                        progressUpdate = method;
                    }
                    break;
                case "onPostExecute":
                    updateUiMethods(heavyUiMethods, allStatementsToRemove, method);
                    if (heavyUiMethods.contains(method)) {
                        postExecute = method;
                    }
                    break;
                default:
                    // Method is not relevant to the code smell, so ignore it
            }
        }

        if (heavyUiMethods.isEmpty()) {
            return null;
        }

        return new HeavyAsyncTaskCodeSmell(classDec, background, preExecute, progressUpdate, postExecute, allStatementsToRemove);
    }

    private static void updateUiMethods(Set<PsiMethod> uiMethods, Set<PsiStatement> allStatementsToRemove, PsiMethod method) {
        Set<PsiStatement> statementsToRemove = checkHeavyUIMethod(method);
        if (!statementsToRemove.isEmpty()) {
            uiMethods.add(method);
            allStatementsToRemove.addAll(statementsToRemove);
        }
    }

    private static Set<PsiStatement> checkHeavyUIMethod(PsiMethod uiMethod) {
        // Check for statements where they are not involved in any UI method call. If they can be moved,
        // flag as HAS code smell.
        AsyncTaskUIMethodVisitor visitor = new AsyncTaskUIMethodVisitor(uiMethod.getParameterList().getParameters());
        uiMethod.accept(visitor);
        Set<PsiStatement> statementsToIgnore = visitor.getStatementsToIgnore();
        Set<PsiStatement> statementsToRemove = new LinkedHashSet<>();
        PsiCodeBlock block = uiMethod.getBody();
        if (block != null) {
            for (PsiStatement statement : block.getStatements()) {
                if (!statementsToIgnore.contains(statement)) {
                    statementsToRemove.add(statement);
                }
            }
        }
        return statementsToRemove;
    }

}
