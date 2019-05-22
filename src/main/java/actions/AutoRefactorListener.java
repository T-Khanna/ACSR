package actions;

import codesmell.CodeSmell;
import com.intellij.ide.BrowserUtil;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationListener;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import utils.Constants;
import utils.Utils;

import javax.swing.event.HyperlinkEvent;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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
                    Utils.refactorCodeSegment(this.project, codeSmell.getMappingFromPsiElementToRefactoring());
                }
            });
        } else if (event.getDescription().equals(Constants.NAVIGATE_TRIGGER)) {
            CodeSmell codeSmell = this.codeSmells.get(0);
            Map<PsiElement, String> refactoringMapping = codeSmell.getMappingFromPsiElementToRefactoring();
            for (PsiElement element : refactoringMapping.keySet()) {
                OpenFileDescriptor fileDescriptor = new OpenFileDescriptor(this.project, element.getContainingFile().getVirtualFile(), element.getTextOffset());
                fileDescriptor.navigate(true);
                break;
            }
        } else {
            BrowserUtil.launchBrowser(event.getURL().toExternalForm());
        }
    }

}
