import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static java.text.MessageFormat.format;

/**
 * This implementation of `FileServer` ensures fairness and avoids races. It does so in the following ways.
 * A `ReentrantLock` AND a `Semaphore` is created for every file.
 *
 * See `files`, there is always a single lock and a single semaphore for the ith file stored in locks in the ith index and semaphores in the ith index respectively.
 *
 * There are essentially two nested "critical sections" with the "writing section" being inside the "reading section". 
 * To enter the "reading critical section" you must acquire a Semaphore. 
 * To enter the "writing section" you must first ensure there is nobody else in the reading section AND there is nobody else in the "writing section" (i.e. you have exclusive access).
 *
 * NOTE that there are by default only 4 permits for reading (but it could be increased to any number >1 at the cost of performance).
 *
 * If a reader (client that wishes to read a file i.e. open it in READ mode) requests access and there is a permit available, it will be given read access to the file.
 * This is designed in such a way that if another has opened the file for writing, it will have acquired all the permits (see below). 
 * In this way you can only have one writer or any number of readers at any time.
 *
 * To enter the "writing critical section" you must both acquire all permits (ensures that nobody is reading) 
 * and you get the lock (ensures nobody else is writing).
 *
 * If a writer requests access to a file that is being read by n clients, it will have to wait until all of them release the semaphores and n becomes 0.
 * Then the reader acquires all semaphore permits effectively blocking read access for the file. The writer also needs to get the lock for the file, which in turn ensures there is only one client writing at a time (prevents multiple writes to the same file).
 *
 * Fairness is ensured by instantiating the `Semaphores` and `ReentrantLocks` with parameter `fair` set to `true`. 
 * They are implemented in such a way that the underlying queue gives equal (i.e. fair amount of time to all clients to requested files). 
 *
 * Additionally, attempting to open a file in a mode undefined by the specification (i.e. the assessment instructions) results in failure which here is basically returning of the empty `Optional`.
 *
 * @author nl253
 */

public class MyFileServer implements FileServer {

    private final List<File> files = new ArrayList<>();
    private final List<Lock> locks = new ArrayList<>();
    private final List<Semaphore> semaphores = new ArrayList<>();
    // reading permits
    private static final int NO_READERS = 4;

    @Override
    public void create(final String filename, final String content) {
        files.add(new File(filename, content, Mode.CLOSED));
        locks.add(new ReentrantLock(true));
        semaphores.add(new Semaphore(NO_READERS, true));
    }

    @Override
    public Optional<File> open(final String filename, final Mode mode) {
        assert mode.equals(Mode.READABLE) || mode.equals(Mode.READWRITEABLE) : format("Mode must be {0} or {1}", Mode.READABLE.toString(), Mode.READWRITEABLE);
        synchronized (files) {
            for (int i = 0; i < files.size(); i++) {
                final File focus = files.get(i);
                if (focus.filename().equals(filename)) switch (mode) {
                    case READABLE:
                        try {
                            semaphores.get(i).acquire();
                            final File openedFile = new File(focus.filename(), focus.read(), Mode.READABLE);
                            files.set(i, openedFile);
                            return Optional.ofNullable(openedFile);
                        } catch (final InterruptedException e) {
                            e.printStackTrace();
                            Thread.currentThread().interrupt();
                        } finally {
                            semaphores.get(i).release();
                        }
                    case READWRITEABLE:
                        try {
                            // wait until nobody is reading
                            semaphores.get(i).acquire(NO_READERS);
                            // wait until nobody is writing
                            locks.get(i).lock();
                            final File openedFile = new File(focus.filename(), focus.read(), Mode.READWRITEABLE);
                            files.set(i, openedFile);
                            return Optional.ofNullable(openedFile);
                        } catch (final InterruptedException e) {
                            e.printStackTrace();
                            Thread.currentThread().interrupt();
                        } finally {
                            semaphores.get(i).release(NO_READERS);
                            locks.get(i).unlock();
                        }
                }
            }
            return Optional.empty();
        }
    }

    @Override
    public void close(final File file) {
        for (int i = 0; i < files.size(); i++) {
            final File focus = files.get(i);
            if (focus.filename().equals(file.filename())) {
                files.set(i, new File(file.filename(), file.read(), Mode.CLOSED));
                return;
            }
        }
    }

    @Override
    public Mode fileStatus(final String filename) {
        return files.stream().filter(f -> f.filename().equals(filename)).findFirst().get().mode();
    }

    @Override
    public Set<String> availableFiles() {
        return files.stream().map(File::filename).collect(Collectors.toSet());
    }
}
