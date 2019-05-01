package detection;

import codesmell.heavyasynctask.HeavyAsyncTaskCodeSmell;
import com.intellij.psi.PsiClass;

public class DetectHeavyAsyncTask {

    public static HeavyAsyncTaskCodeSmell checkForHeavyAsyncTask(PsiClass classDec) {
        // Check class declaration is of an AsyncTask

        // Need to check onPreExecute(), onProgressUpdate() and onPostExecute() methods

        return null;
    }

}
