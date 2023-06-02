package quanta.exception;

import quanta.exception.base.RuntimeEx;

public class NodeAuthFailedException extends RuntimeEx {
    public NodeAuthFailedException() {
        super("Unauthorized");
    }
}
