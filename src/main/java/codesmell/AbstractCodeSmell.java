package codesmell;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.siyeh.ig.psiutils.CommentTracker;

import java.util.HashMap;
import java.util.Map;

public abstract class AbstractCodeSmell implements CodeSmell {

    protected Map<PsiElement, String> refactoringMappings;
    private boolean isRefactoringsUpToDate;

    protected AbstractCodeSmell() {
        this.refactoringMappings = new HashMap<>();
        this.isRefactoringsUpToDate = false;
    }

    protected static int getLineNum(PsiFile psifile, PsiElement element) {
        return StringUtil.offsetToLineNumber(psifile.getText(), element.getTextOffset()) + 1;
    }

    @Override
    public Map<PsiElement, String> getMappingFromPsiElementToRefactoring() {
        if (!this.isRefactoringsUpToDate) {
            updateRefactorings();
            this.isRefactoringsUpToDate = true;
        }
        return this.refactoringMappings;
    }

    protected abstract void updateRefactorings();

}