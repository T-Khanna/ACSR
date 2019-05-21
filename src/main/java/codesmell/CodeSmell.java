package codesmell;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.siyeh.ig.psiutils.CommentTracker;

public interface CodeSmell {

    String getInformativeMessage(PsiFile psiFile);

    String getShortDescription();

    PsiElement getAssociatedPsiElement();

    String getRefactoredCode();

    CommentTracker getCommentTracker();

}