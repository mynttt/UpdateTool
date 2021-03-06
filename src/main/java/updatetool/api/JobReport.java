package updatetool.api;

public class JobReport {
    public enum StatusCode { PASS, ERROR, API_ERROR }

    public final String userDefinedMessage;
    public final StatusCode code;
    public final Throwable exception;

    public JobReport(String userDefinedMessage, StatusCode code, Throwable ex) {
        this.code = code;
        this.userDefinedMessage = userDefinedMessage;
        this.exception = ex;
    }
}