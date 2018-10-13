import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.text.MessageFormat.*;

/**
 * @author nl253
 */

public class Client {

    private static final int ASCII_LOWER_BOUND = 33;
    private static final int ASCII_UPPER_BOUND = 126;
    private static final int NO_ACTIONS = 20;
    private static int nextId = 0;

    public Client(final FileServer fileServer) {
        final int id = nextId;
        nextId++;
        final Logger log = Logger.getAnonymousLogger();
        log.info("client no " + id + " started");
        for (int i = 0; i < NO_ACTIONS; i++) {
            final boolean write = ThreadLocalRandom.current().nextBoolean();
            final String fileName = randFileName(new ArrayList<>(fileServer.availableFiles()));
            log.info(format("client no {0} about to perform {1}{2} action, more specifically {3} on file \"{4}\"", id, i, (i == 1) ? "st" : ((i == 2) ? "nd" : ((i == 3) ? "rd" : "st")), write ? "write" : "read", fileName));
            fileServer.open(fileName, write ? Mode.READWRITEABLE : Mode.READABLE);
        }

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
}
