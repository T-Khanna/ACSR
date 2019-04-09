package actions;

import codesmell.CodeSmell;
import codesmell.slowloop.SlowLoopCodeSmell;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.siyeh.ig.psiutils.CommentTracker;
import visitors.CodeVisitor;

import java.util.List;

public class AddressCodeSmellsAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        // Get all the required data from data keys
        final Project project = e.getProject();
        final PsiFile psifile = e.getData(LangDataKeys.PSI_FILE);
        if (psifile == null) {
            System.err.println("Psifile returned null");
            return;
        }

        List<CodeSmell> identifiedCodeSmells = detectCodeSmells(psifile);

        for (CodeSmell codeSmell : identifiedCodeSmells) {
            // Check if code smell is Slow Loop
            if (codeSmell instanceof SlowLoopCodeSmell) {
                PsiElement element = codeSmell.getAssociatedPsiElement();
                NotificationGroup notifier = new NotificationGroup("acsr", NotificationDisplayType.BALLOON, true);
                // Can get start offset with element.getTextOffset() and then
                // add length of element.getText() to get end offset
                int lineNum = StringUtil.offsetToLineNumber(psifile.getText(), element.getTextOffset()) + 1;
                String info = codeSmell.getInformativeMessage(lineNum);
                notifier.createNotification(
                        "Code Smell",
                        info,
                        NotificationType.INFORMATION,
                        null
                ).notify(project);

                SlowLoopCodeSmell slowLoop = (SlowLoopCodeSmell) codeSmell;
                String refactoredText = slowLoop.getRefactoredCode();
                updateStatement(element, project, slowLoop.getCommentTracker(), refactoredText);
            }
        }

    }

    private static void updateStatement(PsiElement element, Project project, CommentTracker ct, String newText) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            ct.replaceAndRestoreComments(element, newText);
        });
    }

    private static List<CodeSmell> detectCodeSmells(PsiFile psifile) {
        CodeVisitor visitor = new CodeVisitor();
        psifile.accept(visitor);
        return visitor.getIdentifiedCodeSmells();
    }

}
