package utils;

import com.intellij.psi.PsiVariable;

public class Holder {

    public static final Holder DUMMY = new Holder();

    private final PsiVariable variable;

    public Holder(PsiVariable variable) {
        this.variable = variable;
    }

    private Holder() {
        this.variable = null;
    }

    public PsiVariable getVariable() {
        return this.variable;
    }

}