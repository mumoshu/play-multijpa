package play.modules.multijpa;

import javax.persistence.EntityManager;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * JPA Support<br />
 * <br />
 * When the application starts, MultiJPAPlugin creates an instance of DatastoreRegistry for each database configurations.<br />
 * <br />
 * On initialization. DatastoreRegistry creates an EntityManagerFactory for a database configuration.<br />
 * <br />
 * For each request, DatastoreRegistry:<br />
 * - creates an EntityManager fomr the EntityManagerFactory<br />
 * - starts a transaction<br />
 * - query the database<br />
 * - closes the transaction
 *
 * <pre>
 * Thread 1...1 DatastoreRegistry 1...N Datastore
 *
 * Datastore 1...1 DatastoreConfiguration 1...1 Ejb3Configuration
 *                           | creates
 *                  1...1 EntityMangerFactory
 *                           | creates
 *                  1...1 EntityManager
 * </pre>
 */
public class DatastoreRegistry {

    private static ThreadLocal<DatastoreRegistry> currentDatastoreServiceRegistry = new ThreadLocal<DatastoreRegistry>();

    /**
     * A map of EntityManagers for each database name.
     */
    private Map<String, Datastore> datastoreServices = new HashMap<String, Datastore>();

    /**
     * Returns the instance of DatastoreRegistry, dedicated for the current thread.
     * @return
     */
    public static DatastoreRegistry current() {
        DatastoreRegistry registry = currentDatastoreServiceRegistry.get();

        if (registry == null) {
            registry = new DatastoreRegistry();
            //throw new JPAException("The JPA context is not initialized. JPA Entity Manager automatically start when one or more classes annotated with the @javax.persistence.Entity annotation are found in the application.");
        }
        return registry;
    }

    /**
     * Retrive the EntityManager for specific database, and current thread.<br />
     * A transaction automatically starts you gets the EntityManager at first in each invocation.
     * @param databaseName
     * @return
     */
    public static EntityManager getCurrentEntityManager(String databaseName) {
        return current().get(databaseName).getEntityManager();
    }

    /**
     * Retrieve the EntityManager for the dabatase connected with an Entity class, and current thread.<br />
     *
     * @param clazz an instance of Class<T>, which is used to get database name.
     * @param <T> a subclass of Entity
     * @return always not null
     */
    public static <T> EntityManager getCurrentEntityManager(Class<T> clazz) {
        return getCurrentEntityManager(ModelEnhancer.getDatabaseName(clazz));
    }

    /**
     * Retrieve the Datastore for the databaseName.
     * @param databaseName
     * @return null if databaseName which is not defined in application.conf
     */
    public Datastore get(String databaseName) {
        Datastore datastore = datastoreServices.get(databaseName);

        if (datastore != null) {
            return datastore;
        } else {
            datastore = new DatastoreFactory().createDatastore(databaseName);
        }
        return datastore;
    }

    public Collection<Datastore> all() {
        return datastoreServices.values();
    }

    /**
     * Close all transactions.
     * @param rollback true if do rollback
     */
    public void endTransactions(boolean rollback) {
        for (Datastore datastore : all()) {
            datastore.endTransaction(rollback);
        }
    }

    public void clearAllEntityManagers() {
        for (Datastore datastore : all()) {
            datastore.clearContext();
        }
    }
}
