package me.kavin.piped.utils.obj;

public class Comment {

    public String author, thumbnail, commentId, commentText, commentedTime, commentorUrl, repliesPage;
    public int likeCount, replyCount;
    public boolean hearted, pinned, verified, creatorReplied, channelOwner;

    public Comment(String author, String thumbnail, String commentId, String commentText, String commentedTime,
                   String commentorUrl, String repliesPage, int likeCount, int replyCount, boolean hearted, boolean pinned, boolean verified, boolean creatorReplied, boolean channelOwner) {
        this.author = author;
        this.thumbnail = thumbnail;
        this.commentId = commentId;
        this.commentText = commentText;
        this.commentedTime = commentedTime;
        this.commentorUrl = commentorUrl;
        this.repliesPage = repliesPage;
        this.likeCount = likeCount;
        this.replyCount = replyCount;
        this.hearted = hearted;
        this.pinned = pinned;
        this.verified = verified;
        this.creatorReplied = creatorReplied;
        this.channelOwner = channelOwner;
    }
}
