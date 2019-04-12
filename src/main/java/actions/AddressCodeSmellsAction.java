package actions;

import codesmell.AutoRefactorListener;
import codesmell.CodeSmell;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
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
                    new AutoRefactorListener(project, codeSmell)
            ).notify(project);
        }

    }

    private static List<CodeSmell> detectCodeSmells(PsiFile psifile) {
        CodeVisitor visitor = new CodeVisitor();
        psifile.accept(visitor);
        return visitor.getIdentifiedCodeSmells();
    }

}
