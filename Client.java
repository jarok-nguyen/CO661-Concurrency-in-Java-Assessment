import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.System.setProperty;

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
        for (int i = 1; i <= NO_ACTIONS; i++) {
            boolean write = ThreadLocalRandom.current().nextBoolean();
            final String fileName = server.availableFiles().size() > 5 ? randFileName(new ArrayList<>(server.availableFiles())) : randFileName();
            if (!server.availableFiles().contains(fileName)) server.create(fileName, randText());
            log.info(String.format(
                    "%s about to perform %d%s action, more specifically %s on file \"%s\"",
                    toString(),
                    i,
                    i == 1 ? "st" : i == 2 ? "nd" : i == 3 ? "rd" : "st",
                    write ? "write" : "read",
                    fileName));

            final File file = server.open(fileName, write ? Mode.READWRITEABLE : Mode.READABLE).get();

            switch (file.mode()) {
                case READABLE:
                    file.read();
                    log.info(String.format("%s successfully read file \"%s\"", toString(), file.filename()));
                    break;
                case READWRITEABLE:
                    final String content = randText();
                    file.write(content);
                    log.info(String.format("%s successfully wrote %d bytes to file \"%s\"", toString(), content.length(), file.filename()));
                    break;
            }
        }
    }

    public Client(final FileServer fileServer) {
        id = nextId.incrementAndGet();
        server = fileServer;
        log.info(String.format("client no %d created", id));
    }

    private static String randFileName(final List<String> allFiles) {
        return allFiles.get(ThreadLocalRandom.current().nextInt(allFiles.size()));
    }

    private static String randText(final int minLen, final int maxLen) {
        return IntStream
                .range(0, ThreadLocalRandom.current().nextInt(minLen, maxLen))
                .mapToObj(i -> String.valueOf((char) (ASCII_LOWER_BOUND + (ThreadLocalRandom.current().nextInt(ASCII_UPPER_BOUND - ASCII_LOWER_BOUND)))))
                .collect(Collectors.joining());
    }

    private static String randFileName() {
        String s;

        do {
            s = IntStream.range(20, 50)
                    .mapToObj(c -> ((Supplier<Character>) () -> (char) (ASCII_LOWER_BOUND + (ThreadLocalRandom.current().nextInt(ASCII_UPPER_BOUND - ASCII_LOWER_BOUND)))).get())
                    .filter(Character::isAlphabetic)
                    .map(Objects::toString)
                    .collect(Collectors.joining());
        } while (s.length() < 10);

        return s;
    }

    private static String randText() {
        return randText(MIN_RAND_TEXT_LEN, MAX_RAND_TEXT_LEN);
    }

    public static void main(final String... args) throws InterruptedException {
        setProperty("java.util.logging.SimpleFormatter.format", "%1$tH:%1$tM:%1$tS %4$-6s %2$s %5$s%6$s%n");
        final FileServer server = new MyFileServer();
        final List<Client> clients = IntStream.range(0, NO_CLIENTS).mapToObj(n -> new Client(server)).collect(Collectors.toList());
        clients.forEach(Thread::start);
        for (Client client : clients) client.join();
    }

    @Override
    public final String toString() {
        return String.format("Client %d", id);
    }

}
