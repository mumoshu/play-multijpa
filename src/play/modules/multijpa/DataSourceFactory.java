package play.modules.multijpa;

import com.mchange.v2.c3p0.ComboPooledDataSource;
import play.Logger;
import play.Play;
import play.exceptions.DatabaseException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;
import java.sql.*;
import java.util.Properties;

public class DataSourceFactory {

    public DataSource createDataSource(String name) {
        DataSourceConfiguration config = new DataSourceConfiguration(name);

        try {
            DataSource dataSource;

            String db = config.getDB();

            if (db != null && db.startsWith("java:")) {

                Context ctx = new InitialContext();
                dataSource = (DataSource) ctx.lookup(db);

            } else {

                // Try the driver
                String driver = config.getDriver();
                String user = config.getUser();
                String url = config.getUrl();
                String pass = config.getPass();

                try {
                    Driver d = (Driver) Class.forName(driver, true, Play.classloader).newInstance();
                    DriverManager.registerDriver(new ProxyDriver(d));
                } catch (Exception e) {
                    throw new Exception("Driver not found (" + driver + ")");
                }

                // Try the connection
                Connection fake = null;
                try {
                    if (user == null) {
                        fake = DriverManager.getConnection(url);
                    } else {
                        fake = DriverManager.getConnection(url, user, pass);
                    }
                } finally {
                    if (fake != null) {
                        fake.close();
                    }
                }

                System.setProperty("com.mchange.v2.log.MLog", "com.mchange.v2.log.FallbackMLog");
                System.setProperty("com.mchange.v2.log.FallbackMLog.DEFAULT_CUTOFF_LEVEL", "OFF");

                ComboPooledDataSource ds = new ComboPooledDataSource();
                ds.setDriverClass(driver);
                ds.setJdbcUrl(url);
                ds.setUser(user);
                ds.setPassword(pass);
                ds.setAcquireRetryAttempts(10);
                ds.setCheckoutTimeout(Integer.parseInt(config.getOrElse("pool.timeout", "5000")));
                ds.setBreakAfterAcquireFailure(false);
                ds.setMaxPoolSize(Integer.parseInt(config.getOrElse("pool.maxSize", "30")));
                ds.setMinPoolSize(Integer.parseInt(config.getOrElse("pool.minSize", "1")));
                ds.setIdleConnectionTestPeriod(10);
                ds.setTestConnectionOnCheckin(true);

                dataSource = ds;

                // Try the JDBC connection
                String jdbcUrl = ds.getJdbcUrl();
                Connection c = null;
                try {
                    c = ds.getConnection();
                } finally {
                    if (c != null) {
                        c.close();
                    }
                }
                Logger.info("Connected to %s", jdbcUrl);

            }
            return dataSource;
        } catch (Exception e) {
            Logger.error(e, "Cannot connected to the database : %s", e.getMessage());
            if (e.getCause() instanceof InterruptedException) {
                throw new DatabaseException("Cannot connected to the database. Check the configuration.", e);
            }
            throw new DatabaseException("Cannot connected to the database, " + e.getMessage(), e);
        }
    }

    /**
     * Needed because DriverManager will not load a driver ouside of the system classloader
     */
    public static class ProxyDriver implements Driver {

        private Driver driver;

        ProxyDriver(Driver d) {
            this.driver = d;
        }

        public boolean acceptsURL(String u) throws SQLException {
            return this.driver.acceptsURL(u);
        }

        public Connection connect(String u, Properties p) throws SQLException {
            return this.driver.connect(u, p);
        }

        public int getMajorVersion() {
            return this.driver.getMajorVersion();
        }

        public int getMinorVersion() {
            return this.driver.getMinorVersion();
        }

        public DriverPropertyInfo[] getPropertyInfo(String u, Properties p) throws SQLException {
            return this.driver.getPropertyInfo(u, p);
        }

        public boolean jdbcCompliant() {
            return this.driver.jdbcCompliant();
        }
    }
}
