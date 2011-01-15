package play.modules.multijpa;

import javax.persistence.EntityManager;

import play.exceptions.JPAException;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JPA Support<br />
 * <br />
 * When the application starts, MultiJPAPlugin creates an instance of DatastoreServiceRegistry for each database configurations.<br />
 * <br />
 * On initialization. DatastoreServiceRegistry creates an EntityManagerFactory for a database configuration.<br />
 * <br />
 * For each request, DatastoreServiceRegistry:<br />
 * - creates an EntityManager fomr the EntityManagerFactory<br />
 * - starts a transaction<br />
 * - query the database<br />
 * - closes the transaction
 */
public class DatastoreServiceRegistry {

    private static ThreadLocal<DatastoreServiceRegistry> currentDatastoreServiceRegistry = new ThreadLocal<DatastoreServiceRegistry>();

    /**
     * A map of EntityManagers for each database name.
     */
    private Map<String, DatastoreService> datastoreServices = new HashMap<String, DatastoreService>();

    /**
     * Returns the instance of DatastoreServiceRegistry, dedicated for the current thread.
     * @return
     */
    public static DatastoreServiceRegistry current() {
        DatastoreServiceRegistry registry = currentDatastoreServiceRegistry.get();

        if (registry == null) {
            registry = DatastoreServiceRegistryFactory.createDatastoreServiceRegistry();
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

    public void put(String databaseName, DatastoreService datastoreService) {
        datastoreServices.put(databaseName, datastoreService);
    }

    /**
     * Retrieve the DatastoreService for the databaseName.
     * @param databaseName
     * @return null if databaseName which is not defined in application.conf
     */
    public DatastoreService get(String databaseName) {
        return datastoreServices.get(databaseName);
    }

    public Collection<DatastoreService> all() {
        return datastoreServices.values();
    }

    /**
     * Close all transactions.
     * @param rollback true if do rollback
     */
    public void endTransactions(boolean rollback) {
        for (DatastoreService datastoreService : all()) {
            datastoreService.endTransaction(rollback);
        }
    }

    public void clearAllEntityManagers() {
        for (DatastoreService datastoreService : all()) {
            if (datastoreService.isEnabled()) {
                datastoreService.getEntityManager().clear();
            }
        }
    }
}
