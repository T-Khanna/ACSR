package actions;

import codesmell.CodeSmell;
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import visitors.CodeVisitor;

import java.util.LinkedHashSet;
import java.util.Set;

public class PreemptiveCodeSmellDelegate extends TypedHandlerDelegate {

    private static final Set<CodeSmell> identifiedCodeSmells = new LinkedHashSet<>();

    @NotNull
    @Override
    public Result beforeCharTyped(char c, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile, @NotNull FileType fileType) {
        // Custom handler
        CodeVisitor visitor = new CodeVisitor();
        psiFile.accept(visitor);
        Set<CodeSmell> codeSmells = visitor.getIdentifiedCodeSmells();

        for (CodeSmell codeSmell : codeSmells) {
            // If this has already been identified, ignore it.
            if (identifiedCodeSmells.contains(codeSmell)) {
                continue;
            }

            // TODO: Perform the UI Display action upon preemptive-detection
            System.out.println(codeSmell);
        }

        return super.beforeCharTyped(c, project, editor, psiFile, fileType);
    }

    public static void addIdentifiedCodeSmells(Set<CodeSmell> smellsToAdd) {
        identifiedCodeSmells.addAll(smellsToAdd);
    }

}