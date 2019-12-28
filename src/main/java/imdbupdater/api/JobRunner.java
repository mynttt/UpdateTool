package imdbupdater.api;

public interface JobRunner<T extends Job> {
    public JobReport run(T job, Pipeline<T> pipeline);
}