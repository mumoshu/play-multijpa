package play.modules.multijpa;

import javassist.CannotCompileException;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.bytecode.AnnotationsAttribute;
import play.classloading.ApplicationClasses.ApplicationClass;
import play.classloading.enhancers.Enhancer;

import java.lang.annotation.Annotation;

/**
 * Enhance JPABase entities classes
 */
public class JPAEnhancer extends Enhancer {

    /**
     * Find annotation named <code>annotationName</code>
     * @param ctClass
     * @param annotationName
     * @return The annotation found, or null if not found.
     */
    private static Annotation findAnnotation(CtClass ctClass, String annotationName) {
        Object[] annotations = ctClass.getAvailableAnnotations();

        for (Object object : annotations) {
            Annotation annotation = (Annotation) object;

            if (annotation.annotationType().getName().equals(annotationName)) {
                return annotation;
            }
        }
        return null;
    }

    /**
     *
     * @param ctClass Target class contains annotations
     * @param annotationClass Class of annotation to find
     * @param <T>
     * @return
     */
    private static <T> T findAnnotation(CtClass ctClass, Class<T> annotationClass) {
        Annotation annotation = findAnnotation(ctClass, annotationClass.getName());

        if (annotation != null) {
            return (T) annotation;
        } else {
            return null;
        }
    }

    private void makeAndAddGetJQPLMethod(CtClass ctClass) throws CannotCompileException {
        String argument;
        Database database = findAnnotation(ctClass, Database.class);

        if (database != null) {
            argument = "\"" + database.value() + "\"";
        } else {
            argument = "\"default\"";
        }

        CtMethod getJPQL = CtMethod.make("public static JPQL getJQPL() { return play.modules.multijpa.JPQL.getInstance(" + argument + "); }", ctClass);
        ctClass.addMethod(getJPQL);
    }

    public void enhanceThisClass(ApplicationClass applicationClass) throws Exception {
        CtClass ctClass = makeClass(applicationClass);

        if (!ctClass.subtypeOf(classPool.get("play.db.jpa.JPABase"))) {
            return;
        }

        // Enhance only JPA entities
        if (!hasAnnotation(ctClass, "javax.persistence.Entity")) {
            return;
        }

        String entityName = ctClass.getName();

        makeAndAddGetJQPLMethod(ctClass);

        // count
        CtMethod count = CtMethod.make("public static long count() { return getJPQL().count(\"" + entityName + "\"); }", ctClass);
        ctClass.addMethod(count);

        // count2
        CtMethod count2 = CtMethod.make("public static long count(String query, Object[] params) { return getJPQL().count(\"" + entityName + "\", query, params); }", ctClass);
        ctClass.addMethod(count2);

        // findAll
        CtMethod findAll = CtMethod.make("public static java.util.List findAll() { return getJPQL().findAll(\"" + entityName + "\"); }", ctClass);
        ctClass.addMethod(findAll);

        // findById
        CtMethod findById = CtMethod.make("public static play.db.jpa.JPABase findById(Object id) { return getJPQL().findById(\"" + entityName + "\", id); }", ctClass);
        ctClass.addMethod(findById);

        // findBy
        CtMethod findBy = CtMethod.make("public static java.util.List findBy(String query, Object[] params) { return getJPQL().findBy(\"" + entityName + "\", query, params); }", ctClass);
        ctClass.addMethod(findBy);

        // find
        CtMethod find = CtMethod.make("public static play.db.jpa.GenericModel.JPAQuery find(String query, Object[] params) { return getJPQL().find(\"" + entityName + "\", query, params); }", ctClass);
        ctClass.addMethod(find);

        // find
        CtMethod find2 = CtMethod.make("public static play.db.jpa.GenericModel.JPAQuery find() { return getJPQL().find(\"" + entityName + "\"); }", ctClass);
        ctClass.addMethod(find2);

        // all
        CtMethod all = CtMethod.make("public static play.db.jpa.GenericModel.JPAQuery all() { return getJPQL().all(\"" + entityName + "\"); }", ctClass);
        ctClass.addMethod(all);

        // delete
        CtMethod delete = CtMethod.make("public static int delete(String query, Object[] params) { return getJPQL().delete(\"" + entityName + "\", query, params); }", ctClass);
        ctClass.addMethod(delete);

        // deleteAll
        CtMethod deleteAll = CtMethod.make("public static int deleteAll() { return getJPQL().deleteAll(\"" + entityName + "\"); }", ctClass);
        ctClass.addMethod(deleteAll);

        // findOneBy
        CtMethod findOneBy = CtMethod.make("public static play.db.jpa.JPABase findOneBy(String query, Object[] params) { return getJPQL().findOneBy(\"" + entityName + "\", query, params); }", ctClass);
        ctClass.addMethod(findOneBy);

        // create
        CtMethod create = CtMethod.make("public static play.db.jpa.JPABase create(String name, play.mvc.Scope.Params params) { return getJPQL().create(\"" + entityName + "\", name, params); }", ctClass);
        ctClass.addMethod(create);

        // Done.
        applicationClass.enhancedByteCode = ctClass.toBytecode();
        ctClass.defrost();
    }

}
