package actions;

import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
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
//        final Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
//        final Project project = e.getProject();
        final PsiFile psifile = e.getData(LangDataKeys.PSI_FILE);
        if (psifile == null) {
            System.err.println("Psifile returned null");
            return;
        }

        List<PsiElement> suspectElements = detectCodeSmells(psifile);

        for (PsiElement element : suspectElements) {
            // Can get start offset with element.getTextOffset() and then
            // add length of element.getText() to get end offset
            NotificationGroup notifier = new NotificationGroup("acsr", NotificationDisplayType.BALLOON, true);
            int lineNum = StringUtil.offsetToLineNumber(psifile.getText(), element.getTextOffset()) + 1;
            String info = "For loop at line " + lineNum + " is an instance of a Slow Loop code smell. " +
                    "As per the official documentation, it is recommended to use for-each syntax instead";
            notifier.createNotification(
                    "Possible Code Smell",
                    info,
                    NotificationType.INFORMATION,
                    null
                    ).notify(e.getProject());
        }

    }

    private List<PsiElement> detectCodeSmells(PsiFile psifile) {
        CodeVisitor visitor = new CodeVisitor();
        psifile.accept(visitor);
        return visitor.getFlaggedElements();
    }

    private void replaceSelectedText(Editor editor, Project project, String replacement) {
        // Access document, caret, and selection
        final Document document = editor.getDocument();
        final SelectionModel selectionModel = editor.getSelectionModel();

        final int start = selectionModel.getSelectionStart();
        final int end = selectionModel.getSelectionEnd();

        // Making the replacement
        WriteCommandAction.runWriteCommandAction(project,
                () -> document.replaceString(start, end, replacement)
        );

        selectionModel.removeSelection();
    }

}
