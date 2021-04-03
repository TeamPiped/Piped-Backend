package me.kavin.piped.utils.obj;

import java.util.List;

public class CommentsPage {

    public List<Comment> comments;
    public String nextpage;

    public CommentsPage(List<Comment> comments, String nextpage) {
        this.comments = comments;
        this.nextpage = nextpage;
    }
}
