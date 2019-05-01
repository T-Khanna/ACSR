package codesmell;

import com.intellij.psi.PsiElement;
import com.siyeh.ig.psiutils.CommentTracker;

public interface CodeSmell {

    String getInformativeMessage(int lineNum);

    String getAnnotationMessage();

    PsiElement getAssociatedPsiElement();

    String getRefactoredCode();

    CommentTracker getCommentTracker();

}