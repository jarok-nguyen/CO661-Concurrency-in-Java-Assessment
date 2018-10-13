import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static java.text.MessageFormat.format;

/**
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
            if (focus.equals(file)) files.set(i, new File(file.filename(), file.read(), Mode.CLOSED));
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
