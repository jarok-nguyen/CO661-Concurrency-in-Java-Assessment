import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import static java.lang.String.format;
import static java.lang.Thread.currentThread;

/**
 * This implementation of `FileServer` ensures fairness and avoids races. It
 * does so in the following ways. A `ReentrantLock` AND a `Semaphore` is created
 * for every file. The system keeps tracks of the readers (in a set) and writers (single filed since there is only one writer for every file)
 * so that a situation where someone closes twice is avoided.
 * <p>
 * There are essentially two nested "critical sections" with the "writing
 * section" being inside the "reading section". To enter the "reading critical
 * section" you must acquire a Semaphore. To enter the "writing section" you
 * must first ensure there is nobody else in the reading section AND there is
 * nobody else in the "writing section" (i.e. you have exclusive access).
 * If a reader (client that wishes to read a file i.e. open it in READ mode)
 * requests access and there is a permit available, it will be given read access
 * to the file.
 * <p>
 * This is designed in such a way that if another has opened the
 * file for writing, it will have acquired all the permits . In this
 * way you can only have one writer or any number of readers at any time.
 * <p>
 * To enter the "writing critical section" you must both acquire all permits
 * (ensures that nobody is reading) and you get the lock (ensures nobody else is
 * writing).
 * <p>
 * If a writer requests access to a file that is being read by n clients, it
 * will have to wait until all of them release the semaphores and n becomes 0.
 * Then the reader acquires all semaphore permits effectively blocking read
 * access for the file. The writer also needs to get the lock for the file,
 * which in turn ensures there is only one client writing at a time (prevents
 * multiple writes to the same file).
 * <p>
 * NOTE that there are by default only 4 permits for reading for every file (but it could be
 * increased to any number >1 but it would probably cause performance penalty if the number > #CPU_CORES).
 * <p>
 * E.g.: Assume the system is running.
 * 1. There is a file "abc.txt" on the server.
 * 2. We create and start 2 reader additional threads that attempt to open the file "abc.txt".
 * 3. The limit for the semaphore is 4 readers but at present 2 other threads (in addition to those two) are reading. So the number of permit is 4, the number of available permits is 0 and there are currently 4 readers for "abc.txt".
 * 4. Suppose one of the new readers made an error and closed 4 times and not once.
 * 5. Now we have file "abc.txt" with 4 available permits, 4 max permits and 3 files writing. Now we could spawn more readers than intended i.e. >4.
 * <p>
 * This is the type of behaviour that keeping track of readers and the writer avoids.
 * <p>
 * Fairness is ensured by instantiating the `Semaphores` and `ReentrantLocks`
 * with parameter `fair` set to `true`. They are implemented in such a way that
 * the underlying queue gives equal (i.e. fair amount of time to all clients to
 * requested files).
 * <p>
 * Attempting to open a file in a mode undefined by the specification (i.e. the assessment instructions) results in
 * failure which here is basically returning of the empty `Optional`.
 * <p>
 *
 * @author nl253
 */

public final class MyFileServer implements FileServer {

    final class Wrapper {
        String content;
        final Semaphore semaphore = new Semaphore(NO_READERS, true);
        final ReentrantLock lock = new ReentrantLock(true);
        // make sure that the same writer doesn't close > 1
        Optional<Thread> writer = Optional.empty();
        // make sure that the same reader doesn't close > 1
        final Set<Thread> readers = new HashSet<>();


        Wrapper(final String content) {
            this.content = content;
        }

        void setWriter(final Thread writer) {
            this.writer = Optional.ofNullable(writer);
        }

        Optional<Thread> getWriter() {
            return writer;
        }

        String getContent() {
            return content;
        }

        void setContent(final String newContent) {
            this.content = newContent;
        }

        @Override
        public String toString() {
            return format("Wrapper reading permits are [%d/%d], writer is %s", NO_READERS - readers.size(), NO_READERS, writer.map(w -> "" + w.getId()).orElse("<empty>"));
        }
    }

    private final ConcurrentHashMap<String, Wrapper> files = new ConcurrentHashMap<>();

    // reading permits
    private static final int NO_READERS = 4;

    private final Logger log = Logger.getLogger(format("%s logger", getClass().getSimpleName()));

    private String wid() {
        return String.format("Thread #%d", currentThread().getId());
    }

    @Override
    public void create(final String filename, final String content) {
        log.info(format("creating \"%s\"", filename));
        files.put(filename, new Wrapper(content));
    }

    @Override
    public Optional<File> open(final String filename, final Mode mode) {
        log.info(format("request top open \"%s\" in %s mode", filename, mode.name()));

        if (!files.containsKey(filename)) {
            log.warning(format("failed to locate \"%s\"", filename));
            return Optional.empty();
        } else if (!(mode.equals(Mode.READWRITEABLE) || mode.equals(Mode.READABLE))) {
            log.warning(format("%s failed to open \"%s\" in %s mode", currentThread().toString(), filename, mode.name()));
            return Optional.empty();
        }


        final Wrapper wrapper = files.get(filename);
        log.info(format("located %s in the file server", wrapper.toString()));

        if (mode.equals(Mode.READABLE)) {
            try {
                wrapper.semaphore.acquire();
                wrapper.readers.add(currentThread());
                log.info(format("#%d registered as a reader", currentThread().getId()));
            } catch (final InterruptedException e) {
                e.printStackTrace();
                currentThread().interrupt();
            }
            return Optional.of(new File(filename, wrapper.getContent(), Mode.READABLE));
        } else if (mode.equals(Mode.READWRITEABLE)) {
            try {
                wrapper.semaphore.acquire(NO_READERS);
            } catch (final InterruptedException e) {
                e.printStackTrace();
                currentThread().interrupt();
            }
            wrapper.lock.lock();
            wrapper.setWriter(currentThread());
            log.info(format("#%d registered as a writer", currentThread().getId()));
        }
        return Optional.of(new File(filename, wrapper.getContent(), mode));
    }

    @Override
    public void close(final File file) {
        synchronized (file) {

            final Mode mode = fileStatus(file.filename());

            log.info(format("#%s requesting to close \"%s\" which is in %s mode", currentThread().getId(), file.filename(), mode.name()));

            // worker id

            if (mode == Mode.READABLE) {
                final Wrapper wrapper = files.get(file.filename());
                log.info(format("found %s", wrapper.toString()));

                if (wrapper.readers.contains(currentThread())) {

                    synchronized (wrapper) {

                        log.info(format("de-registering reader %s for \"%s\"", wid(), file.filename()));
                        wrapper.readers.remove(currentThread());

                        log.info(format("%s releasing a semaphore for \"%s\"", wid(), file.filename()));
                        wrapper.semaphore.release();
                    }

                } else {
                    log.warning(format("%s tried to release a semaphore for \"%s\" more than once", wid(), file.filename()));
                }
            } else if (mode == Mode.READWRITEABLE) {
                final Wrapper wrapper = files.get(file.filename());
                log.info(format("found %s", wrapper.toString()));
                if (wrapper.getWriter().map(w -> w.equals(currentThread())).orElse(false)) {
                    synchronized (wrapper) {
                        log.info(format("de-registering writer %s for \"%s\"", wid(), file.filename()));
                        wrapper.setWriter(null);

                        log.info(format("%s applying changes to \"%s\"", wid(), file.filename()));
                        wrapper.setContent(file.read());

                        log.info(format("%s releasing %s semaphores for \"%s\"", wid(), NO_READERS, file.filename()));
                        wrapper.semaphore.release(NO_READERS);

                        log.info(format("%s unlocking writing for \"%s\"", wid(), file.filename()));
                        wrapper.lock.unlock();
                    }

                } else if (wrapper.getWriter().isPresent()) {
                    log.warning(format("%s already closed this file, a different writer %s is writing to \"%s\" now", wid(), wrapper.getWriter().get(), file.filename()));

                } else log.warning(format("%s already closed \"%s\"", wid(), file.filename()));

            } else if (mode == Mode.CLOSED) {
                log.warning(format("%s failed to re-close a closed file \"%s\"", wid(), file.filename()));
            }
        }
    }

    @Override
    public Mode fileStatus(final String filename) {
        log.info(format("requesting file %s status", filename));
        if (files.containsKey(filename)) {
            final Wrapper wrapper = files.get(filename);
            log.info(format("found %s", wrapper.toString()));
            synchronized (wrapper) {
                if (wrapper.semaphore.availablePermits() == NO_READERS) return Mode.CLOSED;
                else if (wrapper.lock.isLocked()) return Mode.READWRITEABLE;
                else return Mode.READABLE;
            }
        } else {
            log.warning(format("\"%s\" neither in READABLE nor READWRITEABLE nor CLOSED mode", filename));
            return Mode.UNKNOWN;
        }
    }

    @Override
    public Set<String> availableFiles() {
        log.info(format("%s requesting all file names", wid()));
        return files.keySet();
    }

    @Override
    public String toString() {
        return format("%s with %s files", getClass().getSimpleName(), files.size());
    }
}
