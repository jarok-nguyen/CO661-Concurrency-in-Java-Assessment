// CO661 - Theory and Practice of Concurrency
// School of Computing, University of Kent
// Dominic Orchard & Laura Bocchi 2018

public enum Mode {
  // File is open (or being requested to be opened) and is readable
  READABLE,

  // File is open (or being requested to be opened) and is readable and writable
  READWRITEABLE,

  // File is closed
  CLOSED,

  // File is unknown
  UNKNOWN
}
