package id.xyz.parkease.exception;

import java.sql.SQLException;

public final class SqlState {

    private static final String OVERLAP = "23P01";
    private static final String DEADLOCK = "40P01";

    private SqlState() {}

    public static boolean isOverlapOrDeadlock(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && (OVERLAP.equals(sqlException.getSQLState()) || DEADLOCK.equals(sqlException.getSQLState()))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
