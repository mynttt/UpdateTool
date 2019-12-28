package updatetool.imdb;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import updatetool.common.SqliteDatabaseProvider;
import updatetool.common.Utility;

public class ImdbDatabaseSupport {
    private final SqliteDatabaseProvider provider;

    public ImdbDatabaseSupport(SqliteDatabaseProvider provider) {
        this.provider = provider;
    }

    public static class ImdbMetadataResult {
        public final String guid, imdbId, title, hash;
        public final Integer id, libraryId;
        public String extraData;
        public Double rating;

        private ImdbMetadataResult(ResultSet rs) throws SQLException {
            id = rs.getInt(1);
            libraryId = rs.getInt(2);
            guid = rs.getString(3);
            title = rs.getString(4);
            extraData = rs.getString(5);
            hash = rs.getString(6);
            rating = (Double) rs.getObject(7);
            imdbId = ImdbUtility.extractImdbId(guid);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(imdbId);
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ImdbMetadataResult other = (ImdbMetadataResult) obj;
            return Objects.equals(id, other.id);
        }
    }

    public List<ImdbMetadataResult> requestEntries(long libraryId) {
        try {
            List<ImdbMetadataResult> list = new ArrayList<>();
            var rs = provider.queryFor("SELECT id, library_section_id, guid, title, extra_data, hash, rating from metadata_items WHERE media_item_count = 1 AND library_section_id = " + libraryId);
            while(rs.next())
                list.add(new ImdbMetadataResult(rs));
            return list;
        } catch (SQLException e) {
            throw Utility.rethrow(e);
        }
    }


    public long requestLibraryIdOfUuid(String uuid) {
        try {
            return provider.queryFor("SELECT id FROM library_sections WHERE uuid = '" + uuid + "';").getLong(1);
        } catch (SQLException e) {
            throw Utility.rethrow(e);
        }
    }

    public void requestBatchUpdateOf(List<ImdbMetadataResult> items) {
        boolean success = true;
        try {
            var s = provider.connection.prepareStatement("UPDATE metadata_items SET rating = ?, extra_data = ?, updated_at = DateTime('now') WHERE id = ?");
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
                    provider.connection.commit();
                } else {
                    provider.connection.rollback();
                }
            } catch(SQLException e) {
                throw Utility.rethrow(e);
            }
        }
    }

}