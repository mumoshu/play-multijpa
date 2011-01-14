package play.modules.multijpa;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.FlushModeType;
import javax.persistence.PersistenceException;

import play.db.jpa.JPA;
import play.exceptions.JPAException;

import java.util.HashMap;
import java.util.Map;

/**
 * JPA Support<br />
 * <br />
 * When the application starts, MultiJPAPlugin creates an instance of MultiJPA for each database configurations.<br />
 * <br />
 * On initialization. MultiJPA creates an EntityManagerFactory for a database configuration.<br />
 * <br />
 * For each request, MultiJPA:<br />
 * - creates an EntityManager fomr the EntityManagerFactory<br />
 * - starts a transaction<br />
 * - query the database<br />
 * - closes the transaction
 */
public class MultiJPA {

    public static EntityManagerFactory entityManagerFactory = null;
    public static ThreadLocal<MultiJPA> local = new ThreadLocal<MultiJPA>();
    /**
     * A map of EntityManagers for each database name.
     */
    private Map<String, EntityManager> entityManagers = new HashMap<String, EntityManager>();
    boolean readonly = true;
    /**
     * If true, MultiJPA automatically starts a JPA transaction for each invocation.
     */
    boolean autoTxs = true;

    /**
     * Returns the instance of MultiJPA, dedicated for the current thread.
     * @return
     */
    static MultiJPA get() {
        if (local.get() == null) {
            throw new JPAException("The JPA context is not initialized. JPA Entity Manager automatically start when one or more classes annotated with the @javax.persistence.Entity annotation are found in the application.");
        }
        return local.get();
    }

    static void clearContext() {
        local.remove();
    }

    /**
     * initialize the JPA context and starts a JPA transaction
     *
     * @param readonly true for a readonly transaction
     */
    public static void startTx(boolean readonly) {
        if (!isEnabled()) {
            return;
        }
        EntityManager manager = entityManagerFactory.createEntityManager();
        manager.setFlushMode(FlushModeType.COMMIT);
        manager.setProperty("org.hibernate.readOnly", readonly);
        if (autoTxs) {
            manager.getTransaction().begin();
        }
        createContext(manager, readonly);
    }

    /**
     * Creates and sets the instance of MultiJPA dedicated for the current thread.
     * @param entityManager
     * @param readonly
     */
    static void createContext(EntityManager entityManager, boolean readonly) {
        if (local.get() != null) {
            try {
                local.get().entityManager.close();
            } catch(Exception e) {
                // Let's it fail
            }
            local.remove();
        }
        MultiJPA context = new MultiJPA();
        context.entityManager = entityManager;
        context.readonly = readonly;
        local.set(context);
    }

    // ~~~~~~~~~~~

    /*
     * Retrieve the entityManager for current thread.
     * Create and start an EntityManager if not created yet.
     */
    public static EntityManager em(String databaseName) {
        return get().getEntityManager(databaseName);
    }

    /**
     * Retrive the entity manager for the database name.
     * @param databaseName
     * @return <code>null</code> if application.conf does not contain a database configuration for <code>databaseName</code>.
     */
    private EntityManager getEntityManager(String databaseName) {
        return entityManagers.get(databaseName);
    }

    /*
     * Tell to JPA do not commit the current transaction
     */
    public static void setRollbackOnly() {
        em().getTransaction().setRollbackOnly();
    }

    /**
     * @return true if an entityManagerFactory has started
     */
    public static boolean isEnabled() {
        return entityManagerFactory != null;
    }

    /**
     * Close transactions for the current thread.
     * @param rollback
     */
    public static void closeTransactionsForTheCurrentThread(boolean rollback) {
        if (get() == null) {
            return;
        }
        get().closeTransactions(rollback);
    }

    /**
     * clear current JPA context and transaction
     * @param rollback shall current transaction be committed (false) or cancelled (true)
     */
    public void closeTransactions(boolean rollback) {
        if (!isEnabled()) {
            return;
        }

        for (EntityManager manager: entityManagers.values()) {
            closeTransactions(manager, rollback);
        }
    }

    private void closeTransactions(EntityManager manager, boolean rollback) {
        try {
            if (!autoTxs) {
                return;
            }


            if (manager.getTransaction().isActive()) {
                if (get().readonly || rollback || manager.getTransaction().getRollbackOnly()) {
                    manager.getTransaction().rollback();
                } else {
                    try {
                        if (autoTxs) {
                            manager.getTransaction().commit();
                        }
                    } catch (Throwable e) {
                        for (int i = 0; i < 10; i++) {
                            if (e instanceof PersistenceException && e.getCause() != null) {
                                e = e.getCause();
                                break;
                            }
                            e = e.getCause();
                            if (e == null) {
                                break;
                            }
                        }
                        throw new JPAException("Cannot commit", e);
                    }
                }
            }
        } finally {
            manager.close();
            clearContext();
        }
    }
}
