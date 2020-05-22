package updatetool.api;

import java.util.Map;
import java.util.Objects;

public abstract class Implementation {
    public final String help, desc, usage, id;
    
    public Implementation(String id, String desc, String usage, String help) {
        this.id = id;
        this.desc = desc;
        this.usage = usage;
        this.help = help;
    }
    
    public abstract void bootstrap(Map<String, String> args) throws Exception;
    public abstract Runnable entryPoint();
    public abstract int scheduleEveryHours();

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Implementation other = (Implementation) obj;
        return Objects.equals(id, other.id);
    }
}
