package visitors;

import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiVariable;

import java.util.HashSet;
import java.util.Set;

public class VariableNameVisitor extends JavaRecursiveElementWalkingVisitor {

    private final Set<String> variableNames = new HashSet<>();

    @Override
    public void visitVariable(PsiVariable variable) {
        this.variableNames.add(variable.getName());
        super.visitVariable(variable);
    }

    public Set<String> getVariableNames() {
        return this.variableNames;
    }

}
