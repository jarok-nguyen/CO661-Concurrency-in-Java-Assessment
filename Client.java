import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.String.format;
import static java.lang.System.setProperty;

/**
 * TODO add comments.
 *
 * @author nl253
 */

public final class Client extends Thread {

    private static final int ASCII_LOWER_BOUND = 33;
    private static final int ASCII_UPPER_BOUND = 126;
    private static final int NO_ACTIONS = 2;
    private static final int NO_CLIENTS = 4;
    private static final int MAX_RAND_TEXT_LEN = 2000;
    private static final int MIN_RAND_TEXT_LEN = 100;
    private static AtomicInteger nextId = new AtomicInteger(0);

    private final static Logger log = Logger.getLogger("Client logger");
    private final int id;
    private final FileServer server;


    @Override
    public void run() {
        log.info(format("%s run", toString()));
        for (int i = 1; i <= NO_ACTIONS; i++) {
            boolean write = ThreadLocalRandom.current().nextBoolean();

            final Set<String> availableFiles = server.availableFiles();
            final String fileName = availableFiles.size() > 5 ? randFileName(new ArrayList<>(availableFiles)) : randFileName();

            if (!availableFiles.contains(fileName)) server.create(fileName, randText());
            log.info(format(
                    "%s about to perform %d%s action, more specifically %s on file \"%s\"",
                    toString(),
                    i,
                    i == 1 ? "st" : i == 2 ? "nd" : i == 3 ? "rd" : "th",
                    write ? "write" : "read",
                    fileName));

            final File file = server.open(fileName, write ? Mode.READWRITEABLE : Mode.READABLE).get();

            switch (file.mode()) {
                case READABLE:
                    file.read();
                    log.info(format("%s successfully read file \"%s\"", toString(), file.filename()));
                    break;
                case READWRITEABLE:
                    final String content = randText();
                    file.write(content);
                    log.info(format("%s successfully wrote %d bytes to file \"%s\"", toString(), content.length(), file.filename()));
                    break;
            }
            server.close(file);
        }
        log.info(format("%s finished", toString()));
    }

    public Client(final FileServer fileServer) {
        id = nextId.incrementAndGet();
        server = fileServer;
        log.info(format("%s created", toString()));
    }

    private static String randFileName(final List<String> allFiles) {
        return allFiles.get(ThreadLocalRandom.current().nextInt(allFiles.size()));
    }

    private static String randText(final int minLen, final int maxLen) {
        final StringBuilder builder = new StringBuilder();
        final int len = minLen + ThreadLocalRandom.current().nextInt(maxLen - minLen);

        for (int i = 0; i < len; i++) {
            char candidate = 'a';
            do {
                candidate = (char) (ASCII_LOWER_BOUND + ThreadLocalRandom.current().nextInt(ASCII_UPPER_BOUND - ASCII_LOWER_BOUND));
            } while (!Character.isAlphabetic(candidate));
            builder.append(candidate);
        }

        return builder.toString();
    }

    private static String randFileName() {
        return randText(5, 20);
    }

    private static String randText() {
        return randText(MIN_RAND_TEXT_LEN, MAX_RAND_TEXT_LEN);
    }

    public static void main(final String... args) throws InterruptedException {
        setProperty("java.util.logging.SimpleFormatter.format", "%1$tH:%1$tM:%1$tS %4$-6s %2$s %5$s%6$s%n");
        final FileServer server = new MyFileServer();
        final List<Client> clients = IntStream.range(0, NO_CLIENTS).mapToObj(n -> new Client(server)).collect(Collectors.toList());
        clients.forEach(Thread::start);
        for (final Client client : clients) client.join();
    }

    @Override
    public final String toString() {
        return format("Client #%d (Thread #%d)", id, currentThread().getId());
    }

}
