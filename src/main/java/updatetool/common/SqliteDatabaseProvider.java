package updatetool.common;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.sqlite.SQLiteConnection;

public class SqliteDatabaseProvider implements AutoCloseable  {
    private static final int BUSY_TIMEOUT_MS = 10000;
    public final Connection connection;

    public SqliteDatabaseProvider(String location) {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:"+location);
            ((SQLiteConnection) connection).setBusyTimeout(BUSY_TIMEOUT_MS);
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            throw Utility.rethrow(e);
        }
    }

    public SelectHandle queryFor(String query) throws SQLException {
        var statement = connection.createStatement();
        return new SelectHandle(connection, statement, statement.executeQuery(query));
    }

    @Override
    public void close() throws Exception {
        connection.close();
    }

}
