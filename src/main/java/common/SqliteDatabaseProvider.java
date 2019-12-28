package common;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SqliteDatabaseProvider implements AutoCloseable  {
    public final Connection connection;

    public SqliteDatabaseProvider(String location) {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:"+location);
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            throw Utility.rethrow(e);
        }
    }

    public ResultSet queryFor(String query) throws SQLException {
        var statement = connection.createStatement();
        return statement.executeQuery(query);
    }

    @Override
    public void close() throws Exception {
        connection.close();
    }

}
