package codesmell;

import codesmell.CodeSmell;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.HyperlinkEvent;

public class AutoRefactorListener implements NotificationListener {

    private CodeSmell codeSmell;
    private Project project;

    public AutoRefactorListener(Project project, CodeSmell codeSmell) {
        this.codeSmell = codeSmell;
        this.project = project;
    }

    @Override
    public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
        PsiElement element = this.codeSmell.getAssociatedPsiElement();
        CommentTracker ct = this.codeSmell.getCommentTracker();
        String refactoredCode = this.codeSmell.getRefactoredCode();
        updateStatement(element, this.project, ct, refactoredCode);
    }


    private static void updateStatement(PsiElement element, Project project, CommentTracker ct, String newText) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            ct.replaceAndRestoreComments(element, newText);
        });
    }


}
