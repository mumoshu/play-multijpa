package play.modules.multijpa;

import javax.persistence.EntityManagerFactory;

public class DatastoreFactory {

    public Datastore createDatastore(String dataSourceName) {
        EntityManagerFactory entityManagerFactory = new DatastoreConfiguration(dataSourceName).getEntityManagerFactory();
        return new Datastore(entityManagerFactory);
    }
}
