package utils;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.psiutils.CommentTracker;

import java.util.Map;

public class Utils {

    public static void refactorCodeSegment(Project project, Map<PsiElement, String> refactoringMapping) {
        PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
        for (Map.Entry<PsiElement, String> entry : refactoringMapping.entrySet()) {
            PsiElement element = entry.getKey();
            String newText = entry.getValue();

            CommentTracker ct = new CommentTracker();
            PsiElement newElement = null;
            if (element instanceof PsiStatement) {
                newElement = factory.createStatementFromText(newText, element);
            } else if (element instanceof PsiMethod) {
                newElement = factory.createMethodFromText(newText, element);
            }
            if (newElement != null) {
                ct.replaceAndRestoreComments(element, newElement);
            }
        }
    }

}
