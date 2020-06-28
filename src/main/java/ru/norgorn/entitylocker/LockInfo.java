package ru.norgorn.entitylocker;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class LockInfo {

    public static LockInfo newLocked() {
        LockInfo lock = new LockInfo();
        if (!lock.getLock().tryLock()) { throw new IllegalStateException("Impossible state"); }
        return lock;
    }

    private final ReentrantLock lock;

    private volatile long threadId;

    public LockInfo() {
        this(new ReentrantLock(true), Thread.currentThread().getId());
    }

    public LockInfo(ReentrantLock lock, long threadId) {
        this.lock = lock;
        this.threadId = threadId;
    }

    public ReentrantLock getLock() {
        return lock;
    }

    public long getThreadId() {
        return threadId;
    }

    public boolean tryLock(long timeoutMilliseconds) throws InterruptedException {
        if (lock.tryLock(timeoutMilliseconds, TimeUnit.MILLISECONDS)) {
            threadId = Thread.currentThread().getId();
            return true;
        }
        return false;
    }
}
