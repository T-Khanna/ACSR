package actions;

import codesmell.CodeSmell;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import utils.Constants;

import javax.swing.event.HyperlinkEvent;

public class AutoRefactorListener implements NotificationListener {

    private Project project;
    private CodeSmell codeSmell;

    public AutoRefactorListener(Project project, CodeSmell codeSmell) {
        this.project = project;
        this.codeSmell = codeSmell;
    }

    @Override
    public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
        if (event.getDescription().equals(Constants.REFACTOR_TRIGGER)) {
            PsiElement element = this.codeSmell.getAssociatedPsiElement();
            CommentTracker ct = this.codeSmell.getCommentTracker();
            String refactoredCode = this.codeSmell.getRefactoredCode();
            updateStatement(element, this.project,  ct, refactoredCode);
        } else {
            BrowserUtil.launchBrowser(event.getURL().toExternalForm());
        }
    }

    private static void updateStatement(PsiElement element, Project project, CommentTracker ct, String newText) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            ct.replaceAndRestoreComments(element, newText);
        });
    }

}
