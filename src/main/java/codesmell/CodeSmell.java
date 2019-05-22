package codesmell;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.siyeh.ig.psiutils.CommentTracker;

import java.util.Map;

public interface CodeSmell {

    String getInformativeMessage(PsiFile psiFile);

    String getShortDescription();

    Map<PsiElement, String> getMappingFromPsiElementToRefactoring();

}