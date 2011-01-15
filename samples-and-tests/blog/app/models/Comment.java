package models;

import play.db.jpa.Model;

public class Comment extends Model {
    public Long articleId;
    public String title;
    public String body;
}
