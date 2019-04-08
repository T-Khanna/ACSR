package utils;

import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;

public class Holder {

    public static final Holder DUMMY = new Holder();

    private final PsiVariable variable;

    public Holder(@NotNull PsiVariable variable) {
        this.variable = variable;
    }

    private Holder() {
        variable = null;
    }

    public PsiVariable getVariable() {
        return variable;
    }

}
