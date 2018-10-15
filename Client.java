import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.IntStream;

import static java.lang.System.setProperty;
import static java.text.MessageFormat.format;

/**
 * TODO add comments.
 *
 * @author nl253
 */

public class Client extends Thread {

    private static final int ASCII_LOWER_BOUND = 33;
    private static final int ASCII_UPPER_BOUND = 126;
    private static final int NO_ACTIONS = 20;
    private static final int NO_CLIENTS = 20;
    private static final int MAX_RAND_TEXT_LEN = 2000;
    private static final int MIN_RAND_TEXT_LEN = 100;
    private static AtomicInteger nextId = new AtomicInteger(0);

    private final Logger log = Logger.getAnonymousLogger();
    private final int id;
    private final FileServer server;


    @Override
    public void run() {
        IntStream.range(0, NO_ACTIONS).forEach((int i) -> {
            final boolean write = ThreadLocalRandom.current().nextBoolean();
            final String fileName = randFileName(new ArrayList<>(server.availableFiles()));
            log.info(format("{0} about to perform {1}{2} action, more specifically {3} on file \"{4}\"", toString(), i, i == 1 ? "st" : i == 2 ? "nd" : i == 3 ? "rd" : "st", write ? "write" : "read", fileName));
            final Optional<File> file = server
                    .open(fileName, write ? Mode.READWRITEABLE : Mode.READABLE);
            file.ifPresent((final File f) -> {
                switch (f.mode()) {
                    case READABLE:
                        f.read();
                        log.info(format("{} successfully read file \"{}\"", toString(), f
                                .filename()));
                        break;
                    case READWRITEABLE:
                        final String text = randomText();
                        f.write(text);
                        log.info(format("{} successfully wrote {} bytes to file \"{}\"", toString(), text
                                .length(), f.filename()));
                        break;
                }
            });
        });
    }

    public Client(final FileServer fileServer) {
        id = nextId.incrementAndGet();
        server = fileServer;
        log.info(format("client no {0} created", id));
    }

    private static String randFileName(final List<String> allFiles) {
        return allFiles
                .get(ThreadLocalRandom.current().nextInt(allFiles.size()));
    }

    private static String randomText() {

        final int randTextLen = MIN_RAND_TEXT_LEN + ThreadLocalRandom.current()
                .nextInt(MAX_RAND_TEXT_LEN);

        final StringBuilder builder = new StringBuilder(randTextLen + 1);

        for (int i = 0; i < randTextLen; i++) {
            final char c = (char) (ASCII_LOWER_BOUND + ThreadLocalRandom
                    .current().nextInt(ASCII_UPPER_BOUND - ASCII_LOWER_BOUND));
            builder.append(c);
        }
        return builder.toString();
    }

    public static void main(final String... args) {
        setProperty("java.util.logging.SimpleFormatter.format", "%1$tH:%1$tM:%1$tS %4$-6s %2$s %5$s%6$s%n");
        final FileServer server = new MyFileServer();
        final Deque<Client> clients = new LinkedList<>();
        for (int i = 0; i < NO_CLIENTS; i++) {
            final Client c = new Client(server);
            clients.addLast(c);
            c.start();
        }
        clients.forEach((final Client client) -> {
            try {
                client.join();
            } catch (final InterruptedException e) {
                e.printStackTrace();
                Thread.currentThread().interrupt();
            }
        });
    }

    @Override
    public final String toString() {
        return format("Client {0}", id);
    }
}
