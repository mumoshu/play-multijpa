package models;

import play.db.jpa.Model;

import javax.persistence.Entity;
import java.util.List;

@Entity
public class Article extends Model {
    String title;
    String body;

    public List<Comment> comments() {
        return Comment.find("byArticleId", id).fetch();
    }
}
