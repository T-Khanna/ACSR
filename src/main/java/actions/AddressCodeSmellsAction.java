package actions;

import codesmell.CodeSmell;
import com.intellij.codeInsight.daemon.NavigateAction;
import com.intellij.debugger.actions.JumpToObjectAction;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import visitors.CodeVisitor;

import java.util.*;
import java.util.stream.Collectors;

public class AddressCodeSmellsAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        // Get all the required data from data keys
        final Project project = e.getProject();

        if (project == null) {
            System.err.println("ERROR: NULL PROJECT");
            return;
        }

        PsiManager psiManager = PsiManager.getInstance(project);
        List<PsiFile> files = FileBasedIndex.getInstance()
                .getContainingFiles(FileTypeIndex.NAME, JavaFileType.INSTANCE, GlobalSearchScope.projectScope(project))
                .stream()
                .map(psiManager::findFile)
                .collect(Collectors.toList());

        Map<PsiFile, Set<CodeSmell>> identifiedCodeSmells = new HashMap<>();

        for (PsiFile psiFile : files) {
            if (psiFile != null) {
                Set<CodeSmell> codeSmells = detectCodeSmells(psiFile);
                identifiedCodeSmells.put(psiFile, codeSmells);
                CodeSmellAnnotator.addIdentifiedCodeSmells(codeSmells);
            }
        }

        CodeSmellAnnotator.enable();

        for (Map.Entry<PsiFile, Set<CodeSmell>> entry : identifiedCodeSmells.entrySet()) {
            PsiFile psiFile = entry.getKey();
            for (CodeSmell codeSmell : entry.getValue()) {
                PsiElement element = codeSmell.getAssociatedPsiElement();
                NotificationGroup notifier = new NotificationGroup("acsr", NotificationDisplayType.BALLOON, true);
                // Can get start offset with element.getTextOffset() and then
                // add length of element.getText() to get end offset
                notifier.createNotification(
                        "Code Smell in file " + psiFile.getName(),
                        codeSmell.getInformativeMessage(psiFile),
                        NotificationType.INFORMATION,
                        new AutoRefactorListener(project, codeSmell)
                ).notify(project);
            }
        }

    }

    private static Set<CodeSmell> detectCodeSmells(PsiFile psifile) {
        CodeVisitor visitor = new CodeVisitor();
        psifile.accept(visitor);
        return visitor.getIdentifiedCodeSmells();
    }

}
