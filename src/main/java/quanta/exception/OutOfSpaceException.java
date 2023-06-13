package quanta.exception;

import quanta.exception.base.RuntimeEx;

// todo-1: when we throw this there are times when the client gets an error messgage with no
// indication of the actual problem.
public class OutOfSpaceException extends RuntimeEx {}
