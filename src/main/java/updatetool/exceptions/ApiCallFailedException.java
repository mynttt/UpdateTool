package updatetool.exceptions;

public class ApiCallFailedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ApiCallFailedException(String msg) {
        super(msg);
    }
}
