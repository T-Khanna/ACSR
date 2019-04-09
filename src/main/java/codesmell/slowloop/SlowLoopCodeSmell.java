package codesmell.slowloop;

import codesmell.CodeSmell;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiForStatement;
import com.siyeh.ig.psiutils.CommentTracker;

public abstract class SlowLoopCodeSmell implements CodeSmell {

    protected PsiForStatement forStatement;
    protected CommentTracker commentTracker;

    protected SlowLoopCodeSmell(PsiForStatement forStatement) {
        this.forStatement = forStatement;
        this.commentTracker = new CommentTracker();
    }

    @Override
    public String getInformativeMessage(int lineNum) {
        return "For loop at line " + lineNum + " is an instance of a Slow Loop code smell. " +
                "As per the official documentation, it is recommended to use for-each syntax instead";
    }

    @Override
    public PsiElement getAssociatedPsiElement() {
        return this.forStatement;
    }

    public CommentTracker getCommentTracker() {
        return this.commentTracker;
    }

}