package updatetool.common;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import org.tinylog.Logger;

public class DatabaseSupport {
    private final SqliteDatabaseProvider provider;

    public DatabaseSupport(SqliteDatabaseProvider provider) {
        this.provider = provider;
    }

    public enum NewAgentSeriesType {
        SERIES, SEASON, EPISODE;
    }
    
    public enum LibraryType {
        MOVIE(1),
        SERIES(2);

        private int n;
        
        LibraryType(int n) {
            this.n = n;
        }
        
        public static LibraryType of (int n) {
            for(var s : values())
                if(s.n == n) return s;
            throw new IllegalArgumentException("number not present");
        }
    }
    
    public static class Library {
        public final LibraryType type;
        public final long id;
        public final int items;
        public final String name, uuid, userFields, agent;

        private Library(ResultSet r, SqliteDatabaseProvider p) throws SQLException {
            try(var handle = p.queryFor("SELECT count(*) FROM media_items WHERE library_section_id = " + r.getLong(1))) {
                items = handle.result().getInt(1);
                id = r.getLong(1);
                name = r.getString(2);
                uuid = r.getString(3);
                type = LibraryType.of(r.getInt(4));
                userFields = r.getString(5);
                agent = r.getString(6);
            } catch(SQLException e) {
                throw e;
            }
        }

        @Override
        public String toString() {
            return name + " @ " + items + " item(s)";
        }
    }

    public List<Library> requestMovieLibraries(EnumSet<Capabilities> capabilities) {
        var list = requestLibrary("SELECT id, name, uuid, section_type, user_fields, agent FROM library_sections WHERE section_type = 1 AND agent in (" + Capabilities.formatMovie(capabilities) + ")");
        var it = list.iterator();
        
        while(it.hasNext()) {
            var next = it.next();
            if(!Objects.equals(next.agent, "tv.plex.agents.movie"))
                continue;
            if(next.userFields == null || !next.userFields.contains("pr%3AratingsSource=imdb")) {
                it.remove();
                Logger.info("Skipping new movie agent library '{}' ({}) because it does not use imdb as ratings source. ({})", next.name, next.id, next.userFields);
            }
        }
        
        return list;
    }
    
    public List<Library> requestSeriesLibraries(EnumSet<Capabilities> capabilities) {
        return requestLibrary("SELECT id, name, uuid, section_type, user_fields, agent FROM library_sections WHERE section_type = 2 AND agent IN (" + Capabilities.formatSeries(capabilities) + ")");
    }
    
    private List<Library> requestLibrary(String sql) {
        try(var handle = provider.queryFor(sql)) {
            var list = new ArrayList<Library>();
            while(handle.result().next())
                list.add(new Library(handle.result(), provider));
            return list;
        } catch (SQLException e) {
            throw Utility.rethrow(e);
        } 
    }

}
