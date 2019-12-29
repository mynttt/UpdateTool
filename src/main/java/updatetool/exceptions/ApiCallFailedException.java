package updatetool.exceptions;

public class ApiCallFailedException extends Exception {
    private static final long serialVersionUID = 1L;

    public ApiCallFailedException(String msg) {
        super(msg);
    }
}
