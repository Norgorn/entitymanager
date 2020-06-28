package ru.norgorn.entitylocker;

import org.apache.commons.collections4.CollectionUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.Collections.emptySet;
import static org.apache.commons.lang3.exception.ExceptionUtils.rethrow;

public class EntityLocker<ID> {

    private final ReentrantLock globalLock = new ReentrantLock(true);
    private volatile CountDownLatch globalLatch = new CountDownLatch(0);
    final ConcurrentMap<ID, LockInfo> locks;
    final ConcurrentMap<Long, Set<ID>> locksByThread = new ConcurrentHashMap<>();

    public EntityLocker(ScheduledExecutorService executor) {
        locks = new ConcurrentHashMap<>();
        executor.scheduleWithFixedDelay(this::cleanUp, 10_000, 5_000, TimeUnit.MILLISECONDS);
    }

    public void lockGlobal(long timeoutMilliseconds) {
        try {
            if (!globalLock.tryLock(timeoutMilliseconds, TimeUnit.MILLISECONDS)) {
                throw new TimeoutException("Global lock timeout");
            }
            globalLatch = new CountDownLatch(1);
        } catch (InterruptedException | TimeoutException e) {
            rethrow(e);
        }
    }

    public void unlockGlobal() {
        checkArgument(globalLock.isHeldByCurrentThread(), "Not held by current thread");
        unlockGlobalUnsafe();
    }

    public void lock(ID id, long timeoutMilliseconds) {
        try {
            // let's pretend that it's ok to possibly wait twice timeout
            if (!globalLatch.await(timeoutMilliseconds, TimeUnit.MILLISECONDS)) {
                throw new TimeoutException("Global lock timeout");
            }

            LockInfo lock = locks.computeIfAbsent(id, k -> LockInfo.newLocked());
            checkDeadLocks(id, lock);
            if (lock.getLock().isHeldByCurrentThread()) {
                // if successfully locked - do not tryLock again
                return;
            }
            if (!lock.tryLock(timeoutMilliseconds)) {
                removeFromByThread(id);
                throw new TimeoutException("Lock timeout");
            }
        } catch (InterruptedException | TimeoutException e) {
            rethrow(e);
        }
    }

    public void unlock(ID id) {
        LockInfo lock = locks.get(id);
        checkNotNull(lock, "No active lock for id " + id);
        checkArgument(lock.getLock().isHeldByCurrentThread(),
                "Not held by current thread");
        removeFromByThread(id);
        lock.getLock().unlock();
    }

    void unlockGlobalUnsafe() {
        globalLatch.countDown();
        globalLock.unlock();
    }

    void cleanUp() {
        try {
            locks.values().removeIf(l -> !l.getLock().isLocked());
            locksByThread.values().removeIf(Set::isEmpty);
        } catch (Exception e) {
            // TODO: normal logging
            e.printStackTrace();
        }
    }

    private void checkDeadLocks(ID id, LockInfo lock) {
        long threadId = Thread.currentThread().getId();
        Set<ID> byThread = locksByThread.computeIfAbsent(threadId, k -> new HashSet<>());
        if (threadId != lock.getThreadId()) {
            Set<ID> byOtherThread = locksByThread.getOrDefault(lock.getThreadId(), emptySet());
            if (CollectionUtils.containsAny(byThread, byOtherThread)) {
                throw new IllegalStateException("Possible deadlock on id " + id);
            }
        }
        byThread.add(id);
    }

    private void removeFromByThread(ID id) {
        long threadId = Thread.currentThread().getId();
        locksByThread.computeIfPresent(threadId, (k, s) -> {
            s.remove(id);
            if (s.isEmpty()) { return null; }
            return s;
        });
    }
}
