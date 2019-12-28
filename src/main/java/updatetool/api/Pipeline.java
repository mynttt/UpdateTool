package updatetool.api;

public abstract class Pipeline<T extends Job> {

    public enum PipelineStage {
        CREATED, ANALYSED_DB, ACCUMULATED_META, TRANSFORMED_META, DB_UPDATED, COMPLETED
    }

    public final void invoke(T job) throws Exception {
        switch(job.stage) {
        case ACCUMULATED_META:
            transformMetadata(job);
            break;
        case ANALYSED_DB:
            accumulateMetadata(job);
            break;
        case COMPLETED:
            break;
        case CREATED:
            analyseDatabase(job);
            break;
        case DB_UPDATED:
            updateXML(job);
            break;
        case TRANSFORMED_META:
            updateDatabase(job);
            break;
        default:
            throw new RuntimeException("Invalid stage: " + job.stage);
        }
    }

    public abstract void analyseDatabase(T job) throws Exception;
    public abstract void accumulateMetadata(T job) throws Exception;
    public abstract void transformMetadata(T job) throws Exception;
    public abstract void updateDatabase(T job) throws Exception;
    public abstract void updateXML(T job) throws Exception;
}