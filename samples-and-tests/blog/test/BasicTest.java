import org.junit.*;
import java.util.*;
import play.test.*;
import models.*;

public class BasicTest extends UnitTest {

    @Test
    public void testFindComments() {
        Fixtures.load("data.yml");
        Article article = (Article) Article.find("byTitle", "title").fetch().get(0);
        List<Comment> comments = article.comments();
        assertEquals(2, comments.size());
    }
}
