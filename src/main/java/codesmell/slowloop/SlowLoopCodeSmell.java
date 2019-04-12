package codesmell.slowloop;

import codesmell.AbstractCodeSmell;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiForStatement;

public abstract class SlowLoopCodeSmell extends AbstractCodeSmell {

    protected PsiForStatement forStatement;

    protected SlowLoopCodeSmell(PsiForStatement forStatement) {
        super();
        this.forStatement = forStatement;
    }

    @Override
    public String getInformativeMessage(int lineNum) {
        StringBuilder sb = new StringBuilder();
        sb.append("For loop at line");
        sb.append(' ');
        sb.append(lineNum);
        sb.append(' ');
        sb.append("is an instance of a Slow Loop code smell.");
        sb.append('\n');
        sb.append("As per the official documentation, it is recommended to use for-each syntax instead.");
        sb.append('\n');
        sb.append("Please click <a href=\"x\" target=\"blank\">here</a> to refactor this code smell.");
        return sb.toString();
    }

    @Override
    public PsiElement getAssociatedPsiElement() {
        return this.forStatement;
    }

}