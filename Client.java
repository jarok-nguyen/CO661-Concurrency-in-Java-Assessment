import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.lang.System.setProperty;
import static java.text.MessageFormat.*;

/**
 *
 * TODO add comments.
 *
 * @author nl253
 */

public class Client extends Thread {

    private static final int ASCII_LOWER_BOUND = 33;
    private static final int ASCII_UPPER_BOUND = 126;
    private static final int NO_ACTIONS = 20;
    private static final int NO_CLIENTS = 20;
    private static AtomicInteger nextId = new AtomicInteger(0);

    private final Logger log = Logger.getAnonymousLogger();
    private final int id;
    private final Server server;

    @override
    public void run() {
        for (int i = 0; i < NO_ACTIONS; i++) {
            final boolean write = ThreadLocalRandom.current().nextBoolean();
            final String fileName = randFileName(new ArrayList<>(server.availableFiles()));
            log.info(format("client no {0} about to perform {1}{2} action, more specifically {3} on file \"{4}\"", id, i, (i == 1) ? "st" : ((i == 2) ? "nd" : ((i == 3) ? "rd" : "st")), write ? "write" : "read", fileName));
            server.open(fileName, write ? Mode.READWRITEABLE : Mode.READABLE);
        }
    }

    public Client(final Server fileServer) {
        id = nextId.incrementAndGet();
        server = fileServer;
        log.info("client no " + id + " created");
    }

    private String randFileName(final List<String> allFiles) {
        return allFiles.get(ThreadLocalRandom.current().nextInt(allFiles.size()));
    }

    public String randomText() {
        return IntStream.range(0, 1000)
                .mapToObj(i -> String.valueOf(ASCII_LOWER_BOUND + ThreadLocalRandom.current().nextInt(ASCII_UPPER_BOUND - ASCII_LOWER_BOUND)))
                .collect(Collectors.joining())
                .toString();
    }

    public static void main(final String... args) {
        setProperty("java.util.logging.SimpleFormatter.format", "%1$tH:%1$tM:%1$tS %4$-6s %2$s %5$s%6$s%n");
        final MyFileServer server = new MyFileServer();
        final List<Client> clients = new LinkedList<>();
        for (int i = 0; i < NO_CLIENTS; i++) {
            final Client c = new Client(server);
            clients.append(c);
            c.start();
        }
        clients.forEach(Client::join);
    }
}
