package play.modules.multijpa;

import play.data.binding.Binder;
import play.db.Model;
import play.exceptions.UnexpectedException;

import javax.persistence.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.util.*;

public class ModelLoader implements Model.Factory {

    private Class<? extends Model> clazz;

    public ModelLoader(Class<? extends Model> clazz) {
        this.clazz = clazz;
    }

    private String getDatabaseName() {
        Database database = clazz.getAnnotation(Database.class);

        if (database != null) {
            return database.value();
        } else {
            return "default";
        }
    }

    private EntityManager em() {
        return DatastoreServiceRegistry.getCurrentEntityManager(getDatabaseName());
    }

    public Model findById(Object id) {
        if (id == null) {
            return null;
        }
        try {
            return em().find(clazz, Binder.directBind(id.toString(), Model.Manager.factoryFor(clazz).keyType()));
        } catch (Exception e) {
            // Key is invalid, thus nothing was found
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public List<Model> fetch(int offset, int size, String orderBy, String order, List<String> searchFields, String keywords, String where) {
        String q = "from " + clazz.getName();
        if (keywords != null && !keywords.equals("")) {
            String searchQuery = getSearchQuery(searchFields);
            if (!searchQuery.equals("")) {
                q += " where (" + searchQuery + ")";
            }
            q += (where != null ? " and " + where : "");
        } else {
            q += (where != null ? " where " + where : "");
        }
        if (orderBy == null && order == null) {
            orderBy = "id";
            order = "ASC";
        }
        if (orderBy == null && order != null) {
            orderBy = "id";
        }
        if (order == null || (!order.equals("ASC") && !order.equals("DESC"))) {
            order = "ASC";
        }
        q += " order by " + orderBy + " " + order;
        Query query = em().createQuery(q);
        if (keywords != null && !keywords.equals("") && q.indexOf("?1") != -1) {
            query.setParameter(1, "%" + keywords.toLowerCase() + "%");
        }
        query.setFirstResult(offset);
        query.setMaxResults(size);
        return query.getResultList();
    }

    public Long count(List<String> searchFields, String keywords, String where) {
        String q = "select count(e) from " + clazz.getName() + " e";
        if (keywords != null && !keywords.equals("")) {
            String searchQuery = getSearchQuery(searchFields);
            if (!searchQuery.equals("")) {
                q += " where (" + searchQuery + ")";
            }
            q += (where != null ? " and " + where : "");
        } else {
            q += (where != null ? " where " + where : "");
        }
        Query query = em().createQuery(q);
        if (keywords != null && !keywords.equals("") && q.indexOf("?1") != -1) {
            query.setParameter(1, "%" + keywords.toLowerCase() + "%");
        }
        return Long.decode(query.getSingleResult().toString());
    }

    public void deleteAll() {
        em().createQuery("delete from " + clazz.getName()).executeUpdate();
    }

    public List<Model.Property> listProperties() {
        List<Model.Property> properties = new ArrayList<Model.Property>();
        Set<Field> fields = new LinkedHashSet<Field>();
        Class<?> tclazz = clazz;
        while (!tclazz.equals(Object.class)) {
            Collections.addAll(fields, tclazz.getDeclaredFields());
            tclazz = tclazz.getSuperclass();
        }
        for (Field f : fields) {
            if (Modifier.isTransient(f.getModifiers())) {
                continue;
            }
            if (f.isAnnotationPresent(Transient.class)) {
                continue;
            }
            Model.Property mp = buildProperty(f);
            if (mp != null) {
                properties.add(mp);
            }
        }
        return properties;
    }

    public String keyName() {
        return keyField().getName();
    }

    public Class<?> keyType() {
        return keyField().getType();
    }

    public Object keyValue(Model m) {
        try {
            return keyField().get(m);
        } catch (Exception ex) {
            throw new UnexpectedException(ex);
        }
    }

    //
    Field keyField() {
        Class c = clazz;
        try {
            while (!c.equals(Object.class)) {
                for (Field field : c.getDeclaredFields()) {
                    if (field.isAnnotationPresent(Id.class)) {
                        field.setAccessible(true);
                        return field;
                    }
                }
                c = c.getSuperclass();
            }
        } catch (Exception e) {
            throw new UnexpectedException("Error while determining the object @Id for an object of type " + clazz);
        }
        throw new UnexpectedException("Cannot get the object @Id for an object of type " + clazz);
    }

    String getSearchQuery(List<String> searchFields) {
        String q = "";
        for (Model.Property property : listProperties()) {
            if (property.isSearchable && (searchFields == null || searchFields.isEmpty() ? true : searchFields.contains(property.name))) {
                if (!q.equals("")) {
                    q += " or ";
                }
                q += "lower(" + property.name + ") like ?1";
            }
        }
        return q;
    }

    Model.Property buildProperty(final Field field) {
        Model.Property modelProperty = new Model.Property();
        modelProperty.type = field.getType();
        modelProperty.field = field;
        if (Model.class.isAssignableFrom(field.getType())) {
            if (field.isAnnotationPresent(OneToOne.class)) {
                if (field.getAnnotation(OneToOne.class).mappedBy().equals("")) {
                    modelProperty.isRelation = true;
                    modelProperty.relationType = field.getType();
                    modelProperty.choices = new Model.Choices() {

                        @SuppressWarnings("unchecked")
                        public List<Object> list() {
                            return em().createQuery("from " + field.getType().getName()).getResultList();
                        }
                    };
                }
            }
            if (field.isAnnotationPresent(ManyToOne.class)) {
                modelProperty.isRelation = true;
                modelProperty.relationType = field.getType();
                modelProperty.choices = new Model.Choices() {

                    @SuppressWarnings("unchecked")
                    public List<Object> list() {
                        return em().createQuery("from " + field.getType().getName()).getResultList();
                    }
                };
            }
        }
        if (Collection.class.isAssignableFrom(field.getType())) {
            final Class<?> fieldType = (Class<?>) ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
            if (field.isAnnotationPresent(OneToMany.class)) {
                if (field.getAnnotation(OneToMany.class).mappedBy().equals("")) {
                    modelProperty.isRelation = true;
                    modelProperty.isMultiple = true;
                    modelProperty.relationType = fieldType;
                    modelProperty.choices = new Model.Choices() {

                        @SuppressWarnings("unchecked")
                        public List<Object> list() {
                            return em().createQuery("from " + fieldType.getName()).getResultList();
                        }
                    };
                }
            }
            if (field.isAnnotationPresent(ManyToMany.class)) {
                if (field.getAnnotation(ManyToMany.class).mappedBy().equals("")) {
                    modelProperty.isRelation = true;
                    modelProperty.isMultiple = true;
                    modelProperty.relationType = fieldType;
                    modelProperty.choices = new Model.Choices() {

                        @SuppressWarnings("unchecked")
                        public List<Object> list() {
                            return em().createQuery("from " + fieldType.getName()).getResultList();
                        }
                    };
                }
            }
        }
        if (field.getType().isEnum()) {
            modelProperty.choices = new Model.Choices() {

                @SuppressWarnings("unchecked")
                public List<Object> list() {
                    return (List<Object>) Arrays.asList(field.getType().getEnumConstants());
                }
            };
        }
        modelProperty.name = field.getName();
        if (field.getType().equals(String.class)) {
            modelProperty.isSearchable = true;
        }
        if (field.isAnnotationPresent(GeneratedValue.class)) {
            modelProperty.isGenerated = true;
        }
        return modelProperty;
    }
}