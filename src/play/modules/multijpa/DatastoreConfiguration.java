package play.modules.multijpa;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Level;
import org.hibernate.CallbackException;
import org.hibernate.EmptyInterceptor;
import org.hibernate.collection.PersistentCollection;
import org.hibernate.ejb.Ejb3Configuration;
import org.hibernate.type.Type;
import play.Logger;
import play.Play;
import play.classloading.ApplicationClasses;
import play.db.DB;
import play.db.jpa.JPA;
import play.db.jpa.JPABase;
import play.exceptions.JPAException;
import play.utils.Utils;

import javax.persistence.Entity;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceException;
import javax.sql.DataSource;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

public class DatastoreConfiguration {

    private String databaseName;
    private EntityManagerFactory entityManagerFactory;

    /**
     * Creates a database configuration from application.conf.
     * @param databaseName you must have a configuration with the same name in application.conf
     */
    public DatastoreConfiguration(String databaseName) {
        this.databaseName = databaseName;
    }

    private List<Class> getAllEntityClasses() {
        List<Class> classes = Play.classloader.getAnnotatedClasses(Entity.class);
        if (classes.isEmpty() && Play.configuration.getProperty("jpa.entities", "").equals("")) {
            return classes;
        }
        return classes;
    }

    private List<Class> getEntityClasses(String databaseName) {
        List<Class> classes = new LinkedList<Class>();
        for (Class clazz : getAllEntityClasses()) {
            if (ModelEnhancer.getDatabaseName(clazz).equals(databaseName)) {
                classes.add(clazz);
            }
        }
        return classes;
    }

    /**
     *
     * @return
     */
    private Ejb3Configuration createEjb3Configuration() {
        DataSource dataSource = DatasourceRegistry.get(databaseName);
        String defaultDriverName = Play.configuration.getProperty("db.driver");
        String driverName = Play.configuration.getProperty("db." + databaseName + ".driver");

        if (driverName == null) {
            driverName = defaultDriverName;
        }

        Ejb3Configuration cfg = new Ejb3Configuration();

        cfg.setDataSource(dataSource);

        if (!Play.configuration.getProperty("jpa.ddl", Play.mode.isDev() ? "update" : "none").equals("none")) {
            cfg.setProperty("hibernate.hbm2ddl.auto", Play.configuration.getProperty("jpa.ddl", "update"));
        }

        cfg.setProperty("hibernate.dialect", getDefaultDialect(driverName));
        cfg.setProperty("javax.persistence.transaction", "RESOURCE_LOCAL");

        // Explicit SAVE for JPABase is implemented here
        // ~~~~~~
        // We've hacked the org.hibernate.event.def.AbstractFlushingEventListener line 271, to flush collection update,remove,recreation
        // only if the owner will be saved.
        // As is:
        // if (session.getInterceptor().onCollectionUpdate(coll, ce.getLoadedKey())) {
        //      actionQueue.addAction(...);
        // }
        //
        // This is really hacky. We should move to something better than Hibernate like EBEAN
        cfg.setInterceptor(new EmptyInterceptor() {

            @Override
            public int[] findDirty(Object o, Serializable id, Object[] arg2, Object[] arg3, String[] arg4, Type[] arg5) {
                if (o instanceof JPABase && !((JPABase) o).willBeSaved) {
                    return new int[0];
                }
                return null;
            }

            @Override
            public boolean onCollectionUpdate(Object collection, Serializable key) throws CallbackException {
                if (collection instanceof PersistentCollection) {
                    Object o = ((PersistentCollection) collection).getOwner();
                    if (o instanceof JPABase) {
                        return ((JPABase) o).willBeSaved;
                    }
                } else {
                    System.out.println("HOO: Case not handled !!!");
                }
                return super.onCollectionUpdate(collection, key);
            }

            @Override
            public boolean onCollectionRecreate(Object collection, Serializable key) throws CallbackException {
                if (collection instanceof PersistentCollection) {
                    Object o = ((PersistentCollection) collection).getOwner();
                    if (o instanceof JPABase) {
                        return ((JPABase) o).willBeSaved;
                    }
                } else {
                    System.out.println("HOO: Case not handled !!!");
                }
                return super.onCollectionRecreate(collection, key);
            }

            @Override
            public boolean onCollectionRemove(Object collection, Serializable key) throws CallbackException {
                if (collection instanceof PersistentCollection) {
                    Object o = ((PersistentCollection) collection).getOwner();
                    if (o instanceof JPABase) {
                        return ((JPABase) o).willBeSaved;
                    }
                } else {
                    System.out.println("HOO: Case not handled !!!");
                }
                return super.onCollectionRemove(collection, key);
            }
        });
        if (Play.configuration.getProperty("jpa.debugSQL", "false").equals("true")) {
            org.apache.log4j.Logger.getLogger("org.hibernate.SQL").setLevel(Level.ALL);
        } else {
            org.apache.log4j.Logger.getLogger("org.hibernate.SQL").setLevel(Level.OFF);
        }
        // inject additional  hibernate.* settings declared in Play! configuration
        cfg.addProperties((Properties) Utils.Maps.filterMap(Play.configuration, "^hibernate\\..*"));

        try {
            Field field = cfg.getClass().getDeclaredField("overridenClassLoader");
            field.setAccessible(true);
            field.set(cfg, Play.classloader);
        } catch (Exception e) {
            Logger.error(e, "Error trying to override the hibernate classLoader (new hibernate version ???)");
        }
        for (Class<?> clazz : getEntityClasses(databaseName)) {
            cfg.addAnnotatedClass(clazz);
            Logger.trace("JPA Model for database `" + databaseName + "`: %s", clazz);
        }
        String[] moreEntities = Play.configuration.getProperty("jpa.entities", "").split(", ");
        for (String entity : moreEntities) {
            if (entity.trim().equals("")) {
                continue;
            }
            try {
                cfg.addAnnotatedClass(Play.classloader.loadClass(entity));
            } catch (Exception e) {
                Logger.warn("JPA -> Entity not found: %s", entity);
            }
        }
        for (ApplicationClasses.ApplicationClass applicationClass : Play.classes.all()) {
            if (applicationClass.isClass() || applicationClass.javaPackage == null) {
                continue;
            }
            Package p = applicationClass.javaPackage;
            Logger.info("JPA -> Adding package: %s", p.getName());
            cfg.addPackage(p.getName());
        }
        String mappingFile = Play.configuration.getProperty("jpa.mapping-file", "");
        if (mappingFile != null && mappingFile.length() > 0) {
            cfg.addResource(mappingFile);
        }
        return cfg;
    }

    /**
     * The instance of EntityManagerFactory is create at the first time you call this method.
     * @return not null
     */
    public EntityManagerFactory getEntityManagerFactory() {
        if (entityManagerFactory == null) {
            entityManagerFactory = createEntityManagerFactory();
        }
        return entityManagerFactory;
    }
    
    /**
     *
     * @return not null
     */
    private EntityManagerFactory createEntityManagerFactory() {
        Ejb3Configuration cfg = createEjb3Configuration();

        Logger.trace("Initializing JPA ...");
        try {
            return cfg.buildEntityManagerFactory();
        } catch (PersistenceException e) {
            throw new JPAException(e.getMessage(), e.getCause() != null ? e.getCause() : e);
        }
//        JPQL.instance = new JPQL();
    }

    private static String getDefaultDialect(String driver) {
        String dialect = Play.configuration.getProperty("jpa.dialect");
        if (dialect != null) {
            return dialect;
        } else if (driver.equals("org.hsqldb.jdbcDriver")) {
            return "org.hibernate.dialect.HSQLDialect";
        } else if (driver.equals("com.mysql.jdbc.Driver")) {
            return "play.db.jpa.MySQLDialect";
        } else if (driver.equals("org.postgresql.Driver")) {
            return "org.hibernate.dialect.PostgreSQLDialect";
        } else if (driver.toLowerCase().equals("com.ibm.db2.jdbc.app.DB2Driver")) {
            return "org.hibernate.dialect.DB2Dialect";
        } else if (driver.equals("com.ibm.as400.access.AS400JDBCDriver")) {
            return "org.hibernate.dialect.DB2400Dialect";
        } else if (driver.equals("com.ibm.as400.access.AS390JDBCDriver")) {
            return "org.hibernate.dialect.DB2390Dialect";
        } else if (driver.equals("oracle.jdbc.driver.OracleDriver")) {
            return "org.hibernate.dialect.Oracle9iDialect";
        } else if (driver.equals("com.sybase.jdbc2.jdbc.SybDriver")) {
            return "org.hibernate.dialect.SybaseAnywhereDialect";
        } else if ("com.microsoft.jdbc.sqlserver.SQLServerDriver".equals(driver)) {
            return "org.hibernate.dialect.SQLServerDialect";
        } else if ("com.sap.dbtech.jdbc.DriverSapDB".equals(driver)) {
            return "org.hibernate.dialect.SAPDBDialect";
        } else if ("com.informix.jdbc.IfxDriver".equals(driver)) {
            return "org.hibernate.dialect.InformixDialect";
        } else if ("com.ingres.jdbc.IngresDriver".equals(driver)) {
            return "org.hibernate.dialect.IngresDialect";
        } else if ("progress.sql.jdbc.JdbcProgressDriver".equals(driver)) {
            return "org.hibernate.dialect.ProgressDialect";
        } else if ("com.mckoi.JDBCDriver".equals(driver)) {
            return "org.hibernate.dialect.MckoiDialect";
        } else if ("InterBase.interclient.Driver".equals(driver)) {
            return "org.hibernate.dialect.InterbaseDialect";
        } else if ("com.pointbase.jdbc.jdbcUniversalDriver".equals(driver)) {
            return "org.hibernate.dialect.PointbaseDialect";
        } else if ("com.frontbase.jdbc.FBJDriver".equals(driver)) {
            return "org.hibernate.dialect.FrontbaseDialect";
        } else if ("org.firebirdsql.jdbc.FBDriver".equals(driver)) {
            return "org.hibernate.dialect.FirebirdDialect";
        } else {
            throw new UnsupportedOperationException("I do not know which hibernate dialect to use with "
                    + driver + " and I cannot guess it, use the property jpa.dialect in config file");
        }
    }
}
