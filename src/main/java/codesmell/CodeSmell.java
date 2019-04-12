package codesmell;

import com.intellij.psi.PsiElement;
import com.siyeh.ig.psiutils.CommentTracker;

public interface CodeSmell {

    String getInformativeMessage(int lineNum);

    String getRefactoredCode();

    PsiElement getAssociatedPsiElement();

    CommentTracker getCommentTracker();

}