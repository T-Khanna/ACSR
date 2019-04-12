package codesmell;

import com.siyeh.ig.psiutils.CommentTracker;

public abstract class AbstractCodeSmell implements CodeSmell {

    protected CommentTracker commentTracker;

    protected AbstractCodeSmell() {
        this.commentTracker = new CommentTracker();
    }

    @Override
    public CommentTracker getCommentTracker() {
        return this.commentTracker;
    }

}