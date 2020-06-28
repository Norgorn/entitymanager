package ru.norgorn.entitylocker;

import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;

class EntityLockerTest {

    private ScheduledExecutorService executor = mock(ScheduledExecutorService.class);

    EntityLocker<Long> sut = new EntityLocker<>(executor);

    @Test
    public void test() {
        sut.lock(1L, 1);
        async(() -> {
            sut.lock(2L, 1);
            sut.unlock(2L);
        });

        async(() -> assertException(() -> sut.lock(1L, 1), new TimeoutException("Lock timeout")));
        async(() -> assertException(() -> sut.lock(1L, 1), new TimeoutException("Lock timeout")));

        sut.unlock(1L);

        sut.cleanUp();//sut.locks.get(2L).getLock().tryLock()
        assertTrue(sut.locks.isEmpty());
        assertTrue(sut.locksByThread.isEmpty());
    }

    @Test
    public void testUnlock_whenNotHeldByThread_thenException() {
        async(() -> assertException(() -> sut.unlock(123L), new NullPointerException("No active lock for id 123")));
        async(() -> assertException(() -> sut.unlockGlobal(), new IllegalArgumentException("Not held by current thread")));
    }

    @Test
    public void testGlobalLock() {
        sut.lockGlobal(1);
        async(() -> assertException(() -> sut.lock(1L, 1), new TimeoutException("Global lock timeout")));
        sut.unlockGlobal();
        async(() -> sut.lock(1L, 1));
    }

    private void async(Runnable action) {
        AtomicReference<Throwable> error = new AtomicReference<>();
        try {
            Thread thread = new Thread(action);
            thread.setUncaughtExceptionHandler((e1, e2) -> error.set(e2));
            thread.start();
            thread.join(10);
        } catch (Exception e) {
            fail(e);
        }
        if (error.get() != null) {
            fail(error.get());
        }
    }

    private void assertException(Runnable action, Exception expectedException) {
        try {
            action.run();
            fail("Expected " + expectedException.getClass() + " '" + expectedException.getMessage() + "' but no error");
        } catch (Exception e) {
            if (!e.getClass().equals(expectedException.getClass()) ||
                    !Objects.equals(e.getMessage(), expectedException.getMessage())) {
                e.printStackTrace();
                fail("Expected " + expectedException.getClass() + " '" + expectedException.getMessage() + "'"
                        + ", but got " + e.getClass() + " '" + e.getMessage() + "'");
            }
        }
    }
}