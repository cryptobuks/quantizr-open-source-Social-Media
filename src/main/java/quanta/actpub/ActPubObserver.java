package quanta.actpub;

@FunctionalInterface
public interface ActPubObserver {
    // returns true to continue iteration (false to terminate)
    boolean item(Object obj);
}
