package codesmell;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.siyeh.ig.psiutils.CommentTracker;

public abstract class AbstractCodeSmell implements CodeSmell {

    protected CommentTracker commentTracker;

    protected AbstractCodeSmell() {
        this.commentTracker = new CommentTracker();
    }

    @Override
    public CommentTracker getCommentTracker() {
        return this.commentTracker;
    }

    protected static int getLineNum(PsiFile psifile, PsiElement element) {
        return StringUtil.offsetToLineNumber(psifile.getText(), element.getTextOffset()) + 1;
    }

}