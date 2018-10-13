// CO661 - Theory and Practice of Concurrency
// School of Computing, University of Kent
// Dominic Orchard & Laura Bocchi 2018

import java.util.Optional;
import java.util.Set;

// File server interface
public interface FileServer {

  // Create a new file on the server
	public void create(String filename, String content);

  // Attempt to open a file -- may block if the file is not available at that mode
  // Returns an Optional.empty() if no such file exists
	public Optional<File> open(String filename, Mode mode);

  // Close a file
  public void close(File file);

  // Check on the status of a file (the mode it is currently in)
  public Mode fileStatus(String filename);

  // What files are available on the server?
  public Set<String> availableFiles();

}
