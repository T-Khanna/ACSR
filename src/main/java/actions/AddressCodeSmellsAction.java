package actions;

import codesmell.CodeSmell;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.notification.NotificationDisplayType;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.indexing.FileBasedIndex;
import org.jetbrains.annotations.NotNull;
import utils.Constants;
import visitors.SourceCodeVisitor;

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
            if (psiFile == null) {
                continue;
            }
            Set<CodeSmell> codeSmells = detectCodeSmells(psiFile);
            if (!codeSmells.isEmpty()) {
                identifiedCodeSmells.put(psiFile, codeSmells);
                CodeSmellAnnotator.addIdentifiedCodeSmells(codeSmells);
            }
        }

        int fileCount = 0;
        int smellCount = 0;
        List<CodeSmell> allCodeSmells = new LinkedList<>();
        for (Map.Entry<PsiFile, Set<CodeSmell>> entry : identifiedCodeSmells.entrySet()) {
            PsiFile psiFile = entry.getKey();
            for (CodeSmell codeSmell : entry.getValue()) {
                NotificationGroup notifier = new NotificationGroup("acsr", NotificationDisplayType.BALLOON, true);
                // Can get start offset with element.getTextOffset() and then
                // add length of element.getText() to get end offset
                notifier.createNotification(
                        "Code Smell in file " + psiFile.getName(),
                        codeSmell.getInformativeMessage(psiFile),
                        NotificationType.INFORMATION,
                        new AutoRefactorListener(project, codeSmell)
                ).notify(project);
                allCodeSmells.add(codeSmell);
                smellCount++;
            }
            fileCount++;
        }

        NotificationGroup notifier = new NotificationGroup("acsr", NotificationDisplayType.BALLOON, true);
        // Can get start offset with element.getTextOffset() and then
        // add length of element.getText() to get end offset
        notifier.createNotification(
                getTitle(fileCount, smellCount),
                "To refactor all identified code smells, please click <a href=\"" + Constants.REFACTOR_TRIGGER + "\">here</a>.",
                NotificationType.INFORMATION,
                new AutoRefactorListener(project, allCodeSmells)
        ).notify(project);

    }

    private String getTitle(int fileCount, int smellCount) {
        StringBuilder sb = new StringBuilder();
        sb.append(smellCount);
        sb.append(' ');
        sb.append("code smell");
        if (smellCount == 1) {
            sb.append(' ');
            sb.append("was");
        } else {
            sb.append("s");
            sb.append(' ');
            sb.append("were");
        }
        sb.append(' ');
        sb.append("identified in the project across");
        sb.append(' ');
        sb.append(fileCount);
        sb.append(' ');
        sb.append("file");
        if (fileCount != 1) {
            sb.append("s");
        }
        return sb.toString();
    }

    private static Set<CodeSmell> detectCodeSmells(PsiFile psifile) {
        SourceCodeVisitor visitor = new SourceCodeVisitor();
        psifile.accept(visitor);
        return visitor.getIdentifiedCodeSmells();
    }

}
