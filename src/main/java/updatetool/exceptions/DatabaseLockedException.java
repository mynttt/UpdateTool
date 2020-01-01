package updatetool.exceptions;

public class DatabaseLockedException extends Exception {
    private static final long serialVersionUID = 1L;

    public DatabaseLockedException(String msg) {
        super(msg);
    }
}
