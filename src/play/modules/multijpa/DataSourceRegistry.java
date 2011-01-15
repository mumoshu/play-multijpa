package play.modules.multijpa;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

public class DataSourceRegistry {

    private DataSourceFactory dataSourceFactory = new DataSourceFactory();

    private Map<String, DataSource> dataSources = new HashMap<String, DataSource>();

    /**
     * Retrive the DataSource for the specified name.<br />
     * An instance of DataSource is created when it's not present yet.
     * @param dataSourceName
     * @return not null
     */
    public DataSource get(String dataSourceName) {
        DataSource dataSource = dataSources.get(dataSourceName);

        if (dataSource != null) {
            return dataSource;
        } else {
            dataSource = dataSourceFactory.createDataSource(dataSourceName);
            dataSources.put(dataSourceName, dataSource);
            return dataSource;
        }
    }
}
