package actions;

import codesmell.CodeSmell;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import utils.Constants;

import javax.swing.event.HyperlinkEvent;
import java.util.LinkedList;
import java.util.List;

public class AutoRefactorListener implements NotificationListener {

    private Project project;
    private List<CodeSmell> codeSmells = new LinkedList<>();

    public AutoRefactorListener(Project project, CodeSmell codeSmell) {
        this.project = project;
        this.codeSmells.add(codeSmell);
    }

    public AutoRefactorListener(Project project, List<CodeSmell> codeSmells) {
        this.project = project;
        this.codeSmells = codeSmells;
    }


    @Override
    public void hyperlinkUpdate(@NotNull Notification notification, @NotNull HyperlinkEvent event) {
        if (event.getDescription().equals(Constants.REFACTOR_TRIGGER)) {
            WriteCommandAction.runWriteCommandAction(this.project, () -> {
                for (CodeSmell codeSmell : this.codeSmells) {
                    PsiElement element = codeSmell.getAssociatedPsiElement();
                    CommentTracker ct = codeSmell.getCommentTracker();
                    String newText = codeSmell.getRefactoredCode();
                    ct.replaceAndRestoreComments(element, newText);
                }
            });
        } else if (event.getDescription().equals(Constants.NAVIGATE_TRIGGER)) {
            PsiElement element = this.codeSmells.get(0).getAssociatedPsiElement();
            OpenFileDescriptor fileDescriptor = new OpenFileDescriptor(this.project, element.getContainingFile().getVirtualFile(), element.getTextOffset());
            fileDescriptor.navigate(true);
        } else {
            BrowserUtil.launchBrowser(event.getURL().toExternalForm());
        }
    }

}
