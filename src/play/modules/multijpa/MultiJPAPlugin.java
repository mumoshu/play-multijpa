package play.modules.multijpa;

import java.lang.annotation.Annotation;
import java.util.Map;

import javax.persistence.Entity;
import javax.persistence.NoResultException;
import javax.persistence.Query;

//import play.Invoker.InvocationContext;
import play.classloading.ApplicationClasses.ApplicationClass;
import play.db.Model;
import play.db.jpa.GenericModel;
import play.db.jpa.JPABase;
import play.db.jpa.JPAPlugin;
import play.exceptions.UnexpectedException;

/**
 * JPA Plugin
 */
public class MultiJPAPlugin extends JPAPlugin {

    public static boolean autoTxs = true;

    @Override
    @SuppressWarnings("unchecked")
    public Object bind(String name, Class clazz, java.lang.reflect.Type type, Annotation[] annotations, Map<String, String[]> params) {
        // TODO need to be more generic in order to work with JPASupport
        if (JPABase.class.isAssignableFrom(clazz)) {
            String keyName = Model.Manager.factoryFor(clazz).keyName();
            String idKey = name + "." + keyName;
            if (params.containsKey(idKey) && params.get(idKey).length > 0 && params.get(idKey)[0] != null && params.get(idKey)[0].trim().length() > 0) {
                String id = params.get(idKey)[0];
                try {
                    Query query = DatastoreRegistry.getCurrentEntityManager(clazz).createQuery("from " + clazz.getName() + " o where o." + keyName + " = ?");
                    query.setParameter(1, play.data.binding.Binder.directBind(name, annotations, id + "", Model.Manager.factoryFor(clazz).keyType()));
                    Object o = query.getSingleResult();
                    return GenericModel.edit(o, name, params, annotations);
                } catch (NoResultException e) {
                    // ok
                } catch (Exception e) {
                    throw new UnexpectedException(e);
                }
            }
            return GenericModel.create(clazz, name, params, annotations);
        }
        return super.bind(name, clazz, type, annotations, params);
    }

    @Override
    public Object bind(String name, Object o, Map<String, String[]> params) {
        if (o instanceof JPABase) {
            return GenericModel.edit(o, name, params, null);
        }
        return null;
    }

    @Override
    public void enhance(ApplicationClass applicationClass) throws Exception {
        new ModelEnhancer().enhanceThisClass(applicationClass);
    }

    @Override
    public void onApplicationStart() {
        // DatastoreConfiguration.createEntityManagerFactories();
    }

    @Override
    public void onApplicationStop() {
        // DatastoreRegistry.cleanAllEntityManagerFactories();
    }

    @Override
    public void beforeInvocation() {
        /*boolean readOnly = false;
        Transactional tx = InvocationContext.current().getAnnotation(Transactional.class);
        if (tx != null) {
            readOnly = tx.readOnly();
        }
        startTx(readOnly);*/
    }

    @Override
    public void afterInvocation() {
        endTransactions(false);
    }

    @Override
    public void onInvocationException(Throwable e) {
        endTransactions(true);
    }

    @Override
    public void invocationFinally() {
        // This does nothing if transactions are already ended.
        endTransactions(true);
    }

    private void endTransactions(boolean rollback) {
        DatastoreRegistry.current().endTransactions(rollback);
    }

    @Override
    public Model.Factory modelFactory(Class<? extends Model> modelClass) {
        if (modelClass.isAnnotationPresent(Entity.class)) {
            return new ModelLoader(modelClass);
        }
        return null;
    }

    @Override
    public void afterFixtureLoad() {
        DatastoreRegistry.current().clearAllEntityManagers();
    }


}
