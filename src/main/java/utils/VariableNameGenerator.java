package utils;

// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

/**
 * A convenience helper class to generate unique name for new variable. To use it, call several by* methods in chain, then call
 * the {@link #generate(boolean)} method. The order of by* method calls matters: candidates registered by earlier calls are preferred.
 * It's recommended to have at least one {@link #byName(String...)} call with at least one non-null candidate as the last resort.
 */
public final class VariableNameGenerator {

    private final JavaCodeStyleManager manager;
    private final PsiElement context;
    private final VariableKind kind;
    private final List<String> candidates;

    /**
     * Constructs a new generator
     * @param context the place where new variable will be declared
     * @param kind kind of variable to generate
     */
    public VariableNameGenerator(PsiElement context, VariableKind kind) {
        this.manager = JavaCodeStyleManager.getInstance(context.getProject());
        this.context = context;
        this.kind = kind;
        this.candidates = new LinkedList<>();
    }

    /**
     * Adds name candidates based on type
     * @param type type of newly generated variable
     * @return this generator
     */
    public VariableNameGenerator byType(PsiType type) {
        if (type != null) {
            SuggestedNameInfo info = this.manager.suggestVariableName(this.kind, null, null, type, true);
            this.candidates.addAll(Arrays.asList(info.names));
        }
        return this;
    }

    /**
     * Adds name candidates based on expression
     * @param expression expression which value will be stored to the new variable
     * @return this generator
     */
    public VariableNameGenerator byExpression(PsiExpression expression) {
        if (expression != null) {
            SuggestedNameInfo info = this.manager.suggestVariableName(this.kind, null, expression, null, true);
            this.candidates.addAll(Arrays.asList(info.names));
        }
        return this;
    }

    /**
     * Adds name candidates based on collection/array name
     * @param name of the collection/array which element is represented by newly generated variable
     * @return this generator
     */
    public VariableNameGenerator byCollectionName(String name) {
        if (name != null) {
            String ref = name + "[0]";
            PsiExpression expr = JavaPsiFacade.getElementFactory(this.context.getProject()).createExpressionFromText(ref, this.context);
            byExpression(expr);
        }
        return this;
    }

    /**
     * Adds name candidates based on property name
     * @param names base names which could be used to generate variable name
     * @return this generator
     */
    public VariableNameGenerator byName(String... names) {
        for (String name : names) {
            if (name != null) {
                SuggestedNameInfo info = this.manager.suggestVariableName(this.kind, name, null, null, true);
                this.candidates.addAll(Arrays.asList(info.names));
            }
        }
        return this;
    }

    /**
     * Generates and returns the unique name
     * @param lookForward whether further conflicting declarations should be considered
     * @return a generated variable name
     */
    public String generate(boolean lookForward) {
        String suffixed = this.candidates.isEmpty() ? "v" : this.candidates.get(0);
        for (String candidate : this.candidates) {
            String name = this.manager.suggestUniqueVariableName(candidate, this.context, lookForward);
            if (name.equals(candidate)) {
                return name;
            }
        }
        return suffixed;
    }
}