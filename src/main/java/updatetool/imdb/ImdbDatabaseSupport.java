package updatetool.imdb;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.sqlite.SQLiteException;
import org.tinylog.Logger;
import updatetool.common.SqliteDatabaseProvider;
import updatetool.common.Utility;

public class ImdbDatabaseSupport {
    private final SqliteDatabaseProvider provider;

    public ImdbDatabaseSupport(SqliteDatabaseProvider provider) {
        this.provider = provider;
    }

    public static class ImdbMetadataResult {
      //Id will be resolved in the pipeline and not here
        public String imdbId, extractedId;
        public final String guid, title, hash;
        public final Integer id, libraryId;
        public String extraData;
        public Double rating;
        public boolean resolved;

        private ImdbMetadataResult(ResultSet rs) throws SQLException {
            id = rs.getInt(1);
            libraryId = rs.getInt(2);
            guid = rs.getString(3);
            title = rs.getString(4);
            extraData = rs.getString(5);
            hash = rs.getString(6);
            rating = (Double) rs.getObject(7);
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

        @Override
        public String toString() {
            return "ImdbMetadataResult [imdbId=" + imdbId + ", guid=" + guid + ", title=" + title + ", hash=" + hash
                    + ", id=" + id + ", libraryId=" + libraryId + ", extraData=" + extraData + ", rating=" + rating
                    + "]";
        }
    }

    public List<ImdbMetadataResult> requestEntries(long libraryId) {
        return requestMetadata("SELECT id, library_section_id, guid, title, extra_data, hash, rating from metadata_items WHERE media_item_count = 1 AND library_section_id = " + libraryId);
    }

    public List<ImdbMetadataResult> requestTvSeriesRoot(long libraryId) {
        return requestMetadata("SELECT id, library_section_id, guid, title, extra_data, hash, rating from metadata_items WHERE media_item_count = 0 AND parent_id IS NULL AND library_section_id = " + libraryId);
    }
    
    public List<ImdbMetadataResult> requestTvSeasonRoot(long libraryId) {
        return requestMetadata("SELECT id, library_section_id, guid, title, extra_data, hash, rating from metadata_items WHERE media_item_count = 0 AND parent_id NOT NULL AND library_section_id = " + libraryId);
    }
    
    private List<ImdbMetadataResult> requestMetadata(String query) {
        try(var handle = provider.queryFor(query)){
            List<ImdbMetadataResult> list = new ArrayList<>();
            while(handle.result().next())
                list.add(new ImdbMetadataResult(handle.result()));
            return list;
        } catch (SQLException e) {
            throw Utility.rethrow(e);
        }
    }

    public void requestBatchUpdateOf(List<ImdbMetadataResult> items) throws SQLiteException {
        boolean success = true;
        try(var s = provider.connection.prepareStatement("UPDATE metadata_items SET rating = ?, extra_data = ?, updated_at = DateTime('now') WHERE id = ?")) {
            for(var item : items) {
                s.setDouble(1, item.rating);
                s.setString(2, item.extraData);
                s.setInt(3, item.id);
                s.addBatch();
            }
            int[] records = s.executeBatch();
            for(int c : records) {
                if (c == Statement.EXECUTE_FAILED) {
                    Logger.error("Batch Update failed: " + c + " | All: " + Arrays.toString(records));
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