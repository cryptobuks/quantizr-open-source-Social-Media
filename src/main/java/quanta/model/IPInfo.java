package quanta.model;

public class IPInfo {

    private Object lock = new Object();
    private long lastRequestTime;

    public Object getLock() {
        return this.lock;
    }

    public long getLastRequestTime() {
        return this.lastRequestTime;
    }

    public void setLock(final Object lock) {
        this.lock = lock;
    }

    public void setLastRequestTime(final long lastRequestTime) {
        this.lastRequestTime = lastRequestTime;
    }

    public IPInfo() {}
}
