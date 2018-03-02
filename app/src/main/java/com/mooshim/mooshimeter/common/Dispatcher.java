package com.mooshim.mooshimeter.common;

import android.support.annotation.NonNull;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import timber.log.Timber;

public class Dispatcher {
    private final static String TAG = "DISPATCH";
    private String thread_name;
    private Thread active_thread;
    private Lock lock = new ReentrantLock(true);

    public Dispatcher(String name_arg) {
        thread_name = name_arg;
    }

    private final class NamedThreadFactory implements ThreadFactory {
        public Thread newThread(@NonNull Runnable r) {
            lock.lock();
            active_thread = new Thread(r, thread_name);
            lock.unlock();
            return active_thread;
        }
    }

    private final BlockingQueue<Runnable> worker_tasks = new LinkedBlockingQueue<>();
    private final ExecutorService worker = new ThreadPoolExecutor(
            1,  // Number of worker threads to run
            1,  // Maximum number of worker threads to run
            1,  // Timeout
            TimeUnit.SECONDS, // Timeout units
            worker_tasks, // Queue of runnables
            new NamedThreadFactory() // Thread factory to generate named thread for easy debug
    );

    public void dispatch(final Runnable r) {
        // Preserve the dispatch context
        final Exception context = new Exception();
        // Wrap the runnable
        Callable<?> wrap = () -> {
            try {
                r.run();
            } catch (Exception e) {
                Timber.e("Exception in callback dispatched from: ");
                context.printStackTrace();
                Timber.e("Exception details: " + e.getMessage());
                e.printStackTrace();
                // forward the exception
                throw e;
            }
            return null;
        };
        // TODO: This returns a future which can be used for blocking
        // until the task is complete.  Maybe return it?
        worker.submit(wrap);
    }

    public boolean isCallingThread() {
        boolean rval;
        lock.lock();
        rval = Thread.currentThread() == active_thread;
        lock.unlock();
        return rval;
    }
}
