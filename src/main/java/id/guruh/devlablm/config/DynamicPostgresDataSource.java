package id.guruh.devlablm.config;

import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;

public class DynamicPostgresDataSource implements DataSource {

    private static final Logger log = LoggerFactory.getLogger(DynamicPostgresDataSource.class);

    private volatile HikariDataSource delegate;

    public synchronized void setDelegate(HikariDataSource newDelegate) {
        HikariDataSource old = this.delegate;
        this.delegate = newDelegate;
        if (old != null && !old.isClosed()) {
            try {
                old.close();
            } catch (Exception e) {
                log.warn("Gagal menutup pool PostgreSQL lama: {}", e.getMessage());
            }
        }
    }

    public HikariDataSource getDelegate() {
        return this.delegate;
    }

    public boolean isConfigured() {
        return this.delegate != null && !this.delegate.isClosed();
    }

    @Override
    public Connection getConnection() throws SQLException {
        if (!isConfigured()) {
            throw new SQLException("Koneksi PostgreSQL belum dikonfigurasi atau belum aktif. Silakan atur di Database Setting.");
        }
        return delegate.getConnection();
    }

    @Override
    public Connection getConnection(String username, String password) throws SQLException {
        if (!isConfigured()) {
            throw new SQLException("Koneksi PostgreSQL belum dikonfigurasi atau belum aktif. Silakan atur di Database Setting.");
        }
        return delegate.getConnection(username, password);
    }

    @Override
    public PrintWriter getLogWriter() throws SQLException {
        return isConfigured() ? delegate.getLogWriter() : null;
    }

    @Override
    public void setLogWriter(PrintWriter out) throws SQLException {
        if (isConfigured()) {
            delegate.setLogWriter(out);
        }
    }

    @Override
    public void setLoginTimeout(int seconds) throws SQLException {
        if (isConfigured()) {
            delegate.setLoginTimeout(seconds);
        }
    }

    @Override
    public int getLoginTimeout() throws SQLException {
        return isConfigured() ? delegate.getLoginTimeout() : 0;
    }

    @Override
    public java.util.logging.Logger getParentLogger() throws SQLFeatureNotSupportedException {
        if (isConfigured()) {
            return delegate.getParentLogger();
        }
        throw new SQLFeatureNotSupportedException("PostgreSQL DataSource belum aktif");
    }

    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        if (isConfigured()) {
            return delegate.unwrap(iface);
        }
        throw new SQLException("PostgreSQL DataSource belum dikonfigurasi");
    }

    @Override
    public boolean isWrapperFor(Class<?> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return true;
        }
        if (isConfigured()) {
            return delegate.isWrapperFor(iface);
        }
        return false;
    }
}
