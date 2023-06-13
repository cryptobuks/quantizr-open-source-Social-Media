package quanta.util;

import quanta.mongo.MongoSession;

/**
 * Runs a unit of work in a specific mongo session, and return an object. Used in Java-8 "Lambda" call pattern.
 */
public interface MongoRunnableEx<T> {
    public T run(MongoSession ms);
}
