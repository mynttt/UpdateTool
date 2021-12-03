package updatetool.gui;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.tinylog.Logger;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import net.harawata.appdirs.AppDirsFactory;

public class Config implements Serializable {

    private static final long serialVersionUID = 4324565028197014877L;

    private enum OS { WIN, NIX };
    private transient static final OS PROCESS_HOST = (System.getProperty("os.name").toLowerCase().contains("win")) ? OS.WIN : OS.NIX;
    private transient static final String APPLICATION_IDENTIFIER = "UpdateToolGUI";
    public transient static final Path APPLICATION_CONFIG = PROCESS_HOST == OS.WIN ? Paths.get(AppDirsFactory.getInstance().getUserConfigDir(APPLICATION_IDENTIFIER, null, APPLICATION_IDENTIFIER, true)).getParent() : Paths.get(AppDirsFactory.getInstance().getUserConfigDir(APPLICATION_IDENTIFIER, null, APPLICATION_IDENTIFIER, true)).toAbsolutePath();

    public static final Config INSTANCE;
    
    private static Config load(String conf) {
        try {
            Files.createDirectories(APPLICATION_CONFIG);
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        
        Logger.info("Loading config: {}", APPLICATION_CONFIG.toAbsolutePath().toString());
        
        if(Files.exists(APPLICATION_CONFIG.resolve(conf))) {
            try(var ois = new ObjectInputStream(Files.newInputStream(APPLICATION_CONFIG.resolve(conf)))) {
                return (Config) ois.readObject();
            } catch(Exception e) {
                e.printStackTrace();
                System.exit(-1);
            }
        }
        return new Config();
    }
    
    static {
        String conf = "CONFIG.SER";
        INSTANCE = load(conf);
        Runtime.getRuntime().addShutdownHook(new Thread(() ->  {
            try(var oos = new ObjectOutputStream(Files.newOutputStream(APPLICATION_CONFIG.resolve(conf)))) {
                oos.writeObject(INSTANCE);
            } catch(Exception e) {
                e.printStackTrace();
            }
        }));
    }
    
    private Config() {
        init();
    }
    
    private void init() {
        plexFolder = new SimpleStringProperty("");
        tmdbKey = new SimpleStringProperty(""); 
        tvdbKey = new SimpleStringProperty("");
        ignoreLibs = new SimpleStringProperty("");
        tvShowOptIn = new SimpleStringProperty("");
        javabinary = new SimpleStringProperty("");
        hours = new SimpleStringProperty("12");
        plexNativeSqlPath = new SimpleStringProperty("");
        capabilities = new SimpleStringProperty("");
        useTmdb = new SimpleBooleanProperty();
        useTvdb = new SimpleBooleanProperty();
        ignoreMovies = new SimpleBooleanProperty();
        ignoreTv = new SimpleBooleanProperty();
        usePlexNativeSql = new SimpleBooleanProperty();
        autostartOnOpen = new SimpleBooleanProperty();
        plexNativeSqlPath.addListener((o, oo, n) -> plexNativeSqlPathV = n.trim());
        usePlexNativeSql.addListener((o, oo, n) -> usePlexNativeSqlV = n);
        capabilities.addListener((o, oo, n) -> capabilitiesV = n.trim());
        hours.addListener((o, oo, n) -> hoursV = n.trim());
        plexFolder.addListener((o, oo, n) -> plexFolderV = n.trim());
        tmdbKey.addListener((o, oo, n) -> tmdbKeyV = n.trim());
        tvdbKey.addListener((o, oo, n) -> tvdbKeyV = n.trim());
        ignoreLibs.addListener((o, oo, n) -> ignoreLibsV = n.trim());
        tvShowOptIn.addListener((o, oo, n) -> tvShowOptInV = n.trim());
        javabinary.addListener((o, oo, n) -> javabinaryV = n.trim());
        useTmdb.addListener((o, oo, n) -> useTmdbV = n);
        useTvdb.addListener((o, oo, n) -> useTvdbV = n);
        ignoreMovies.addListener((o, oo, n) -> ignoreMoviesV = n);
        ignoreTv.addListener((o, oo, n) -> ignoreTvV = n);
        autostartOnOpen.addListener((o, oo, n) -> autostartOnOpenV = n);
    }
    
    private Object readResolve() {
        init();
        plexFolder.set(plexFolderV);
        tmdbKey.set(tmdbKeyV);
        tvdbKey.set(tvdbKeyV);
        ignoreLibs.set(ignoreLibsV);
        tvShowOptIn.set(tvShowOptInV);
        javabinary.set(javabinaryV);
        useTmdb.set(useTmdbV);
        useTvdb.set(useTvdbV);
        ignoreMovies.set(ignoreMoviesV);
        ignoreTv.set(ignoreTvV);
        hours.set(hoursV);
        usePlexNativeSql.set(usePlexNativeSqlV);
        plexNativeSqlPath.set(plexNativeSqlPathV == null ? "" : plexNativeSqlPathV);
        capabilities.set(capabilitiesV == null ? "" : capabilitiesV);
        autostartOnOpen.set(autostartOnOpenV);
        return this;
    }
    
    private transient SimpleStringProperty plexFolder, tmdbKey,tvdbKey, ignoreLibs, javabinary, hours, tvShowOptIn, plexNativeSqlPath, capabilities;
    private transient SimpleBooleanProperty useTmdb,useTvdb,ignoreMovies, ignoreTv, usePlexNativeSql, autostartOnOpen;

    private String 
        plexFolderV = "", 
        tmdbKeyV = "", 
        tvdbKeyV = "", 
        ignoreLibsV = "",
        tvShowOptInV = "",
        hoursV = "12",
        javabinaryV = "",
        plexNativeSqlPathV = "",
        capabilitiesV = "";

    private boolean  
        useTmdbV, 
        useTvdbV, 
        ignoreMoviesV, 
        ignoreTvV,
        usePlexNativeSqlV,
        autostartOnOpenV;

    public SimpleStringProperty getCapabilities() {
        return capabilities;
    }

    public SimpleStringProperty getPlexFolder() {
        return plexFolder;
    }

    public SimpleStringProperty getTmdbKey() {
        return tmdbKey;
    }

    public SimpleStringProperty getTvdbKey() {
        return tvdbKey;
    }

    public SimpleStringProperty getIgnoreLibs() {
        return ignoreLibs;
    }

    public SimpleStringProperty getJavabinary() {
        return javabinary;
    }

    public SimpleBooleanProperty getUseTmdb() {
        return useTmdb;
    }

    public SimpleBooleanProperty getUseTvdb() {
        return useTvdb;
    }

    public SimpleBooleanProperty getIgnoreMovies() {
        return ignoreMovies;
    }

    public SimpleBooleanProperty getIgnoreTv() {
        return ignoreTv;
    }

    public SimpleStringProperty getHours() {
        return hours;
    }

    public Property<String> getTvShowOptIn() {
        return tvShowOptIn;
    }

    public SimpleStringProperty getPlexNativeSqlPath() {
        return plexNativeSqlPath;
    }

    public SimpleBooleanProperty getAutostartOnOpen() {
        return autostartOnOpen;
    }

    public SimpleBooleanProperty getUsePlexNativeSql() {
        return usePlexNativeSql;
    }
}