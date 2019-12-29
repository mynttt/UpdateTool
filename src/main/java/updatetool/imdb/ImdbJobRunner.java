package updatetool.imdb;

import java.util.Objects;
import updatetool.api.JobReport;
import updatetool.api.JobRunner;
import updatetool.api.Pipeline;
import updatetool.api.JobReport.StatusCode;
import updatetool.api.Pipeline.PipelineStage;
import updatetool.exceptions.ApiCallFailedException;
import updatetool.exceptions.RatelimitException;

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
                if(t instanceof ApiCallFailedException)
                    return new JobReport("Aborted job queue due to the API failing to deliver a result.", StatusCode.API_ERROR, t);
                return new JobReport(t.getMessage(), StatusCode.ERROR, t);
            }
        }

        return new JobReport("Job finished correctly", StatusCode.PASS, null);
    }

}
