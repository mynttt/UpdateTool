package common;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DatabaseSupport {
    private final SqliteDatabaseProvider provider;

    public DatabaseSupport(SqliteDatabaseProvider provider) {
        this.provider = provider;
    }

    public class LibraryItem {
        public final long id;
        public final int items;
        public final String name;
        public final String uuid;

        private LibraryItem(ResultSet r, SqliteDatabaseProvider p) throws SQLException {
            var rs = p.queryFor("SELECT count(*) FROM media_items WHERE library_section_id = " + r.getLong(1));
            items = rs.getInt(1);
            id = r.getLong(1);
            name = r.getString(2);
            uuid = r.getString(3);
        }

        @Override
        public String toString() {
            return name + " @ " + items + " item(s)";
        }
    }

    public List<LibraryItem> requestLibraries() {
        try {
            var list = new ArrayList<LibraryItem>();
            var rl = provider.queryFor("SELECT id, name, uuid FROM library_sections WHERE section_type = 1 AND agent = 'com.plexapp.agents.imdb'");
            while(rl.next())
                list.add(new LibraryItem(rl, provider));
            return list;
        } catch (SQLException e) {
            throw Utility.rethrow(e);
        }
    }

}
