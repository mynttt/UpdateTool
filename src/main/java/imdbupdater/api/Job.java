package imdbupdater.api;

import java.util.Objects;
import imdbupdater.api.Pipeline.PipelineStage;

public abstract class Job {
    public String library;
    public String uuid;
    public PipelineStage stage = PipelineStage.CREATED;

    public Job(String library, String uuid) {
        this.library = library;
        this.uuid = uuid;
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
