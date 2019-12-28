package imdbupdater.imdb;

import java.util.Objects;
import imdbupdater.api.JobReport;
import imdbupdater.api.JobReport.StatusCode;
import imdbupdater.api.JobRunner;
import imdbupdater.api.Pipeline;
import imdbupdater.api.Pipeline.PipelineStage;
import imdbupdater.exceptions.RatelimitException;

public class ImdbJobRunner implements JobRunner<ImdbJob> {

    @Override
    public JobReport run(ImdbJob job, Pipeline<ImdbJob> pipeline) {
        Objects.requireNonNull(job);

        while(job.stage != PipelineStage.COMPLETED) {
            try {
                pipeline.invoke(job);
            } catch(Throwable t) {
                if(t instanceof RatelimitException)
                    return new JobReport("Aborted job queue due to being rate limited. Either change the API key or wait a while to continue.", StatusCode.RATE_LIMIT, null);
                return new JobReport(null, StatusCode.ERROR, t);
            }
        }

        return new JobReport("Job finished correctly", StatusCode.PASS, null);
    }

}
