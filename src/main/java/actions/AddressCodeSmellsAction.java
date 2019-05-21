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
            SourceCodeVisitor sourceCodeVisitor = new SourceCodeVisitor();
            psiFile.accept(sourceCodeVisitor);
            Set<CodeSmell> codeSmells = sourceCodeVisitor.getIdentifiedCodeSmells();
            if (!codeSmells.isEmpty()) {
                identifiedCodeSmells.put(psiFile, codeSmells);
            }
        }

        int fileCount = 0;
        int smellCount = 0;

        List<CodeSmell> allCodeSmells = new LinkedList<>();

        for (Map.Entry<PsiFile, Set<CodeSmell>> entry : identifiedCodeSmells.entrySet()) {
            PsiFile psiFile = entry.getKey();
            Set<CodeSmell> codeSmells = entry.getValue();
            for (CodeSmell codeSmell : codeSmells) {
                NotificationGroup notifier = new NotificationGroup("acsr", NotificationDisplayType.BALLOON, true);
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
        notifier.createNotification(
                getTitle(fileCount, smellCount),
                getContent(),
                NotificationType.INFORMATION,
                new AutoRefactorListener(project, allCodeSmells)
        ).notify(project);

    }

    @NotNull
    private String getContent() {
        StringBuilder sb = new StringBuilder();
        sb.append("To refactor all identified code smells, please click <a href=\"");
        sb.append(Constants.REFACTOR_TRIGGER);
        sb.append("\">here</a>.");
        return sb.toString();
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


}
