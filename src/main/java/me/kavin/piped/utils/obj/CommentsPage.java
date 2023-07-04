package me.kavin.piped.utils.obj;

import java.util.List;

public class CommentsPage {

    public List<Comment> comments;
    public String nextpage;
    public boolean disabled;
    public int commentCount;

    public CommentsPage(List<Comment> comments, String nextpage, boolean disabled, int commentCount) {
        this.comments = comments;
        this.nextpage = nextpage;
        this.disabled = disabled;
        this.commentCount = commentCount;
    }
}
