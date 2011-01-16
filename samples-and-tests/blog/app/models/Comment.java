package models;

import play.db.jpa.Model;
import play.modules.multijpa.Database;

import javax.persistence.Entity;

@Entity
@Database("mysql")
public class Comment extends Model {
    public Long articleId;
    public String title;
    public String body;
}
