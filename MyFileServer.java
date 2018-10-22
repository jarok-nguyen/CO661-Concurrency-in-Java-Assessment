import java.util.HashMap;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

import static java.lang.String.format;

/**
 * This implementation of `FileServer` ensures fairness and avoids races. It
 * does so in the following ways. A `ReentrantLock` AND a `Semaphore` is created
 * for every file.
 * <p>
 * See `files`, there is always a single lock and a single semaphore for the ith
 * file stored in locks in the ith index and semaphores in the ith index
 * respectively.
 * <p>
 * There are essentially two nested "critical sections" with the "writing
 * section" being inside the "reading section". To enter the "reading critical
 * section" you must acquire a Semaphore. To enter the "writing section" you
 * must first ensure there is nobody else in the reading section AND there is
 * nobody else in the "writing section" (i.e. you have exclusive access).
 * <p>
 * NOTE that there are by default only 4 permits for reading (but it could be
 * increased to any number >1 at the cost of performance).
 * <p>
 * If a reader (client that wishes to read a file i.e. open it in READ mode)
 * requests access and there is a permit available, it will be given read access
 * to the file. This is designed in such a way that if another has opened the
 * file for writing, it will have acquired all the permits (see below). In this
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
 * Fairness is ensured by instantiating the `Semaphores` and `ReentrantLocks`
 * with parameter `fair` set to `true`. They are implemented in such a way that
 * the underlying queue gives equal (i.e. fair amount of time to all clients to
 * requested files).
 * <p>
 * Additionally, attempting to open a file in a mode undefined by the
 * specification (i.e. the assessment instructions) results in failure which
 * here is basically returning of the empty `Optional`.
 *
 * @author nl253
 */

public class MyFileServer implements FileServer {

    class Wrapper {
        String content;
        final Semaphore semaphore = new Semaphore(NO_READERS, true);
        final ReentrantLock lock = new ReentrantLock(true);

        Wrapper(final String content) {
            this.content = content;
        }

        String getContent() {
            return content;
        }

        void setContent(final String newContent) {
            this.content = newContent;
        }
    }

    private final HashMap<String, Wrapper> files = new HashMap<>();

    // reading permits
    private static final int NO_READERS = 4;

    private final Logger log = Logger.getAnonymousLogger();


    @Override
    public final void create(final String filename, final String content) {
        log.info(format("creating file %s", filename));
        synchronized (files) {
            files.put(filename, new Wrapper(content));
        }
    }

    @SuppressWarnings("AlibabaSwitchStatement")
    @Override
    public final Optional<File> open(final String filename, final Mode mode) {
        log.info(format("opening %s in %s mode", filename, mode.name()));
        if (!files.containsKey(filename)) try {
            throw new Exception(format("failed to locate %s", filename));
        } catch (final Exception e) {
            e.printStackTrace();
            return Optional.empty();
        }

        log.info(format("located %s in the file server", filename));

        switch (mode) {
            case READABLE:
                try {
                    files.get(filename).semaphore.acquire();
                } catch (final InterruptedException e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt();
                }
                break;
            case READWRITEABLE:
                try {
                    // wait until nobody is reading
                    files.get(filename).semaphore.acquire(NO_READERS);
                } catch (final InterruptedException e) {
                    e.printStackTrace();
                    Thread.currentThread().interrupt();
                }
                // wait until nobody is writing
                files.get(filename).lock.lock();
                break;
            default:
                log.warning(format("trying to open in %s mode", mode.name()));
                return Optional.empty();

        }
        return Optional.of(new File(filename, files.get(filename).getContent(), mode));
    }

    @Override
    public void close(final File file) {
        log.info(format("closing file %s", file));

        final Wrapper wrapper = files.get(file.filename());

        switch (fileStatus(file.filename())) {
            case READABLE:
                if (wrapper.semaphore.availablePermits() < NO_READERS) {
                    wrapper.semaphore.release();
                    log.info(format("released a semaphore for %s", file.filename()));
                } else try {
                    throw new Exception("tried to release a semaphore more than once");
                } catch (final Exception e) {
                    e.printStackTrace();
                }
                return;
            case READWRITEABLE:
                try {
                    if (wrapper.semaphore.availablePermits() != 0) {
                        throw new Exception(format("was expecting number of semaphore permits for %s to be 0 but was %d", file.filename(), wrapper.semaphore.availablePermits()));
                    } else if (!wrapper.lock.isLocked()) {
                        throw new Exception(format("was expecting the write lock for %s to be locked but it wasn't!", file.filename()));
                    } else {
                        log.info(format("applying changes to file %s", file.filename()));
                        wrapper.setContent(file.read());
                        wrapper.semaphore.release(NO_READERS);
                        log.info(format("released %s semaphores for %s", NO_READERS, file.filename()));
                        wrapper.lock.unlock();
                        log.info(format("unlocked writing for %s", file.filename()));
                    }
                } catch (final Exception e) {
                    e.printStackTrace();
                }
                return;
            default:
                try {
                    throw new Exception(String.format("trying to close %s in %s mode", file.filename(), file.mode()));
                } catch (final Exception e) {
                    e.printStackTrace();
                }
                return;

        }
    }

    @Override
    public Mode fileStatus(final String filename) {
        log.info(format("requesting file %s status", filename));
        if (files.containsKey(filename)) {
            final Wrapper wrapper = files.get(filename);
            if (wrapper.semaphore.availablePermits() == NO_READERS) return Mode.CLOSED;
            else if (wrapper.lock.isLocked()) return Mode.READWRITEABLE;
            else return Mode.READABLE;
        } else return Mode.UNKNOWN;
    }

    @Override
    public Set<String> availableFiles() {
        log.info("requesting all file names");
        return files.keySet();
    }

    @Override
    public final String toString() {
        return format("MyFileServer with %s files", files.size());
    }
}
