package updatetool.api;

import java.util.Objects;
import updatetool.api.Pipeline.PipelineStage;
import updatetool.common.DatabaseSupport.Library;
import updatetool.common.DatabaseSupport.LibraryType;

public abstract class Job {
    public String library;
    public String uuid;
    public LibraryType libraryType;
    public PipelineStage stage = PipelineStage.CREATED;

    public Job(Library library) {
        this.library = library.name;
        this.uuid = library.uuid;
        this.libraryType = library.type;
    }

    public abstract String whatKindOfJob();

    @Override
    public String toString() {
        return String.format("%-20s | %-15s | %-15s ", library, stage, whatKindOfJob());
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(uuid);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Job other = (Job) obj;
        return Objects.equals(uuid, other.uuid);
    }
}
