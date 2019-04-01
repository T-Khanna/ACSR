package utils;

import com.intellij.psi.*;

public class ForLoopUtils {

    public static boolean isValidForEachType(PsiType type) {
        return type instanceof PsiArrayType ||
               type instanceof PsiClassType && isInstanceOfIterable((PsiClassType) type);
    }

    private static boolean isInstanceOfIterable(PsiClassType classType) {
        PsiClass instance = classType.resolve();
        if (instance == null) {
            return false;
        }
        if (instance.isInterface()) {
            // Base case: Interface is Iterable
            if (classType.getClassName().equals("Iterable")) {
                return true;
            }
        } else {
            // Check if class implements an interface that is/extends Iterable
            for (PsiClassType implementType : instance.getImplementsListTypes()) {
                if (isInstanceOfIterable(implementType)) {
                    return true;
                }
            }
        }
        // Check for the following scenarios:
        // 1. The current interface extends an interface that is/extends Iterable
        // 2. The current class extends a class that implements an interface that is/extends Iterable
        for (PsiClassType extendType : instance.getExtendsListTypes()) {
            if (isInstanceOfIterable(extendType)) {
                return true;
            }
        }
        // At this point all possible checks have been done, and the type is not an instance
        // of Iterable, so return false here
        return false;
    }

}
