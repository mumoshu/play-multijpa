package play.modules.multijpa;

import javax.persistence.EntityManager;
import java.util.HashMap;
import java.util.Map;

/**
 * play.db.jpa.JPQLの複数DB対応版
 */
public class JPQL extends play.db.jpa.JPQL {
    private static Map<String, JPQL> instances = new HashMap<String, JPQL>();

    private String databaseName = null;

    @Override
    public EntityManager em() {
        return MultiJPA.em(databaseName);
    }

    public JPQL(String databaseName) {
        this.databaseName = databaseName;
    }

    /**
     * Returns a JPQL instance dedicated for the current thread.
     *
     * @return An instance of JPQL, not null.
     */
    public static JPQL getInstance() {
       return getInstance("default");
    }

    /**
     * Returns a JPQL instance for a specific database.<br />
     * The JPQL instance is dedicated for the current thread.
     *
     * @param databaseName The name of a database defined in application.conf.
     * @return An instance of JPQL, not null.
     */
    public static JPQL getInstance(String databaseName) {
        return instances.get(databaseName);
    }
}
