package play.modules.multijpa;

import javax.persistence.Cache;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnitUtil;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.metamodel.Metamodel;
import java.util.Map;

public class DummyEntityManagerFactory implements EntityManagerFactory {
    public EntityManager createEntityManager() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public EntityManager createEntityManager(Map map) {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public CriteriaBuilder getCriteriaBuilder() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Metamodel getMetamodel() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public boolean isOpen() {
        return false;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public void close() {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    public Map<String, Object> getProperties() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public Cache getCache() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public PersistenceUnitUtil getPersistenceUnitUtil() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}
