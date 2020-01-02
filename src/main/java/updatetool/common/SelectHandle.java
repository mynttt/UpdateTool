package updatetool.common;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.tinylog.Logger;

public class SelectHandle implements AutoCloseable {
    private final Connection connection;
    private final Statement statement;
    private final ResultSet rs;

    SelectHandle(Connection connection, Statement statement, ResultSet rs) {
        this.connection = connection;
        this.statement = statement;
        this.rs = rs;
    }

    public ResultSet result() {
        return rs;
    }

    public void close() {
        try {
            statement.close();
            connection.commit();
        } catch (SQLException e) {
            Logger.error("Failed to close handle.");
            Logger.error(e);
        }
    }

}
