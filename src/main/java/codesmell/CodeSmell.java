package codesmell;

import com.intellij.psi.PsiElement;

public interface CodeSmell {

    String getInformativeMessage(int lineNum);

    String getRefactoredCode();

    PsiElement getAssociatedPsiElement();

}