package ImdbUpdater;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PlexDatabaseSupport implements AutoCloseable {
    private final Connection connection;

    public static class MetadataResult {
        public final String guid, imdbId, title, hash;
        public final Integer id, libraryId;
        public String extraData;
        public Double rating;

        private MetadataResult(ResultSet rs) throws SQLException {
            id = rs.getInt(1);
            libraryId = rs.getInt(2);
            guid = rs.getString(3);
            title = rs.getString(4);
            extraData = rs.getString(5);
            hash = rs.getString(6);
            rating = (Double) rs.getObject(7);
            imdbId = Utility.extractImdbId(guid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            MetadataResult other = (MetadataResult) obj;
            return Objects.equals(id, other.id);
        }
    }

    public class LibraryItem {
        public final long id;
        public final int items;
        public final String name;
        public final String uuid;

        private LibraryItem(ResultSet r) throws SQLException {
            var rs = queryFor("SELECT count(*) FROM media_items WHERE library_section_id = " + r.getLong(1));
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

    public PlexDatabaseSupport(String location) {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:"+location);
            connection.setAutoCommit(false);
        } catch (SQLException e) {
            throw Utility.rethrow(e);
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<LibraryItem> requestLibraries() {
        try {
            var list = new ArrayList<LibraryItem>();
            var rl = queryFor("SELECT id, name, uuid FROM library_sections WHERE section_type = 1 AND agent = 'com.plexapp.agents.imdb'");
            while(rl.next())
                list.add(new LibraryItem(rl));
            return list;
        } catch (SQLException e) {
            throw Utility.rethrow(e);
        }
    }

    public List<MetadataResult> requestEntries(long libraryId) {
        try {
            List<MetadataResult> list = new ArrayList<>();
            var rs = queryFor("SELECT id, library_section_id, guid, title, extra_data, hash, rating from metadata_items WHERE media_item_count = 1 AND library_section_id = " + libraryId);
            while(rs.next())
                list.add(new MetadataResult(rs));
            return list;
        } catch (SQLException e) {
            throw Utility.rethrow(e);
        }
    }


    public long requestLibraryIdOfUuid(String uuid) {
        try {
            return queryFor("SELECT id FROM library_sections WHERE uuid = '" + uuid + "';").getLong(1);
        } catch (SQLException e) {
            throw Utility.rethrow(e);
        }
    }

    public void requestBatchUpdateOf(List<MetadataResult> items) {
        boolean success = true;
        try {
            var s = connection.prepareStatement("UPDATE metadata_items SET rating = ?, extra_data = ?, updated_at = DateTime('now') WHERE id = ?");
            for(var item : items) {
                s.setDouble(1, item.rating);
                s.setString(2, item.extraData);
                s.setInt(3, item.id);
                s.addBatch();
            }
            int[] records = s.executeBatch();
            for(int c : records) {
                if (c == Statement.EXECUTE_FAILED) {
                    success = false;
                    break;
                }
            }
        } catch (SQLException e) {
            success = false;
            throw Utility.rethrow(e);
        } finally {
            try {
                if(success) {
                    connection.commit();
                } else {
                    connection.rollback();
                }
            } catch(SQLException e) {
                throw Utility.rethrow(e);
            }
        }
    }

    private ResultSet queryFor(String query) throws SQLException {
        var statement = connection.createStatement();
        return statement.executeQuery(query);
    }
}