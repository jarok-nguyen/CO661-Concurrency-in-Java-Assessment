import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Optional;
import java.lang.Thread.State;
import java.util.concurrent.Semaphore;

import static java.lang.System.setProperty;

public class TestSuite {

	// **** Tests top-level ******************************************************

	public void tests() {
		describe("Test file creation and listing");
		testAvailableFiles();

		describe("Test single threaded open/read/write/close");
		testSingleThread();

		describe("Test multithreading locking");
		testMultiThreadRead();
		testMultiThread1();
		// NOTE this one deadlocks
		testMultiThread2();
	}


	public void testAvailableFiles() {
		FileServer fs = newFileServer();

		// Base
		it("No available files");
		assertEquals(fs.availableFiles().size(), 0);
		// Step
		it("One available file. Successful creation");
		fs.create("a", "hello");
		assertEquals(fs.availableFiles().toArray(new String[0]), new String[] {"a"});
		it("Initially closed file");
		assertEquals(fs.fileStatus("a"), Mode.CLOSED);

		// Step step
		it("Two available files");
		fs.create("b", "hello2");
		assertEquals(fs.availableFiles().toArray(new String[0]), new String[] {"a", "b"});

		it("Unknown file status");
		assertEquals(fs.fileStatus("c"), Mode.UNKNOWN);

		it("Opening a file (read) keeps the same available files");
		Optional<File> of = fs.open("a", Mode.READABLE);
		assertEquals(fs.availableFiles().toArray(new String[0]), new String[] {"a", "b"});

		fs.close(of.get());
		it("File still avilable after closing");
		assertEquals(fs.availableFiles().toArray(new String[0])[0], "a");

		it("Opening a file (write) keeps the same available files");
		fs.open("a", Mode.READWRITEABLE);
		assertEquals(fs.availableFiles().toArray(new String[0]), new String[] {"a", "b"});
	}

	public void testSingleThread() {
		FileServer fs = newFileServer();
		fs.create("a", "hello");
		fs.create("b", "world");

		it("Read mode test -- successful open");
		Optional<File> f = fs.open("a", Mode.READABLE);
		assertEquals(f.isPresent(), true);

		it("Read mode test -- successful read");
		assertEquals(f.get().read(), "hello");

		it("Read mode test -- file status");
		assertEquals(fs.fileStatus("a"), Mode.READABLE);

		it("Read mode test -- Closing");
		fs.close(f.get());
		assertEquals(fs.fileStatus("a"), Mode.CLOSED);

		it("Write mode test -- successful open");
		Optional<File> ofw = fs.open("a", Mode.READWRITEABLE);
		assertEquals(f.isPresent(), true);

		it("Write mode test -- file status");
		assertEquals(fs.fileStatus("a"), Mode.READWRITEABLE);

		it("Write mode test -- writing works");
		File fw = ofw.get();
		fw.write("wibble");

		fs.close(fw);

		Optional<File> ofw2 = fs.open("a", Mode.READABLE);
		assertEquals(ofw2.get().read(), "wibble");
		fs.close(ofw2.get());

		it("Reclosing a file in the wrong mode (i.e. now reading file but trying to close write mode file) does not change the state or its contents");
		try {
			fw.write("plop");
			fs.close(fw);
			Optional<File> ofwp = fs.open("a", Mode.READABLE);
			assertEquals(ofwp.get().read(), "wibble");
			fs.close(ofwp.get());

		} catch (Exception e) {
			failure("");
			e.printStackTrace();

		}


		it("Write mode test -- non interference with other files");
		Optional<File> ofb = fs.open("b", Mode.READWRITEABLE);
		assertEquals(ofb.get().read(), "world");

		it("Write mode test -- two independent files open for write allowed");
		Optional<File> ofa = fs.open("a", Mode.READWRITEABLE);
		assertEquals(ofa.isPresent(), true);

		it("Write mode test -- two independent files non-intefering");
		File fb = ofb.get();
		fb.write("wobble");
		fs.close(fb);
		File fa = ofa.get();
		fa.write("waggle");
		fs.close(fa);
		Optional<File> ofa2 = fs.open("a", Mode.READABLE);
		Optional<File> ofb2 = fs.open("b", Mode.READABLE);
		assertEquals(ofa2.get().read(), "waggle");
		assertEquals(ofb2.get().read(), "wobble");
		fs.close(ofa2.get());
		fs.close(ofb2.get());

		it("Closed status");
		assertEquals(fs.fileStatus("a"), Mode.CLOSED);
		assertEquals(fs.fileStatus("b"), Mode.CLOSED);

		it("Reclosing a file doesn't do anything");
		fs.close(ofa2.get());
		assertEquals(fs.fileStatus("a"), Mode.CLOSED);

		it("Opening a file in closed mode returns Optional.empty()");
		Optional<File> ff = fs.open("a", Mode.CLOSED);
		assertEquals(ff.isPresent(), false);

		it("Opening a file in unknown mode returns Optional.empty()");
		Optional<File> fg = fs.open("a", Mode.UNKNOWN);
		assertEquals(fg.isPresent(), false);
	}

	public void testMultiThreadRead() {
		/*
		   Create server with 'a and 'b'

		    Open 'a' for R . Close
		 | Open 'a' for R . Close
		 | Open 'b' for R

		   Check that there is no blocking
		 */

		FileServer fs = newFileServer();
		fs.create("a", "coheed");
		fs.create("b", "cambria");

		Thread t1 = new Thread(new Runnable() {
			@Override
			public void run() {
			        it("Multi threads can read consistent state (thread 1)");
			        try {
			                Optional<File> ofa1 = fs.open("a", Mode.READABLE);
			                File fa1 = ofa1.get();

			                assertEquals(fa1.read(), "coheed");
			                fs.close(fa1);
				} catch (Exception e) {
			                failure("");
			                e.printStackTrace();

				}
			}
		});
		Thread t2 = new Thread(new Runnable() {
			@Override
			public void run() {
			        it("Multi threads can read consistent state (thread 2)");
			        try {
			                Optional<File> ofa2 = fs.open("a", Mode.READABLE);
			                File fa2 = ofa2.get();

			                assertEquals(fa2.read(), "coheed");
			                fs.close(fa2);
				} catch (Exception e) {
			                failure("");
			                e.printStackTrace();

				}
			}
		});
		Thread t3 = new Thread(new Runnable() {
			@Override
			public void run() {
			        it("Multi threads can read (different file state) (thread 3)");
			        try {
			                Optional<File> ofa3 = fs.open("b", Mode.READABLE);
			                File fa3 = ofa3.get();
			                assertEquals(fa3.read(), "cambria");
				} catch (Exception e) {
			                failure("");
			                e.printStackTrace();

				}
			}
		});
		t1.start();
		t2.start();
		t3.start();
		try {
			t1.join(300);
			t2.join(300);
			t3.join(300);
		} catch (InterruptedException e) { assertEquals(false, true); }
		it("Multiple read is allowed with no blocking (thread 1)");
		assertEquals(t1.getState(), Thread.State.TERMINATED);

		it("Multiple read is allowed with no blocking (thread 2)");
		assertEquals(t2.getState(), Thread.State.TERMINATED);

		it("Multiple read (to another file) is allowed with no blocking (thread 3)");
		assertEquals(t3.getState(), Thread.State.TERMINATED);
	}

	public void testMultiThread1() {
		FileServer fs = newFileServer();
		fs.create("a", "coheed");
		fs.create("b", "cambria");


		// -----------------------------------------------------------------------------
		// Scenario 1:
		//   Thread main: opens A for reading
		//   Thread wt1: open A for writing
		//             - GETS BLOCKED
		//   Thread main: closes A
		//   Thread wt1: -- GETS UNBLOCKED
		//                closes A

		Thread main = new Thread(new Runnable() {
			@Override
			public void run() {

			        Optional<File> ofA = fs.open("a", Mode.READABLE);

			        Signal signal = new Signal();
			        signal.flag = false;

			        // Start off another thread which gets blocked
			        Thread wt1 = new Thread(new Runnable() {

					@Override
					public void run() {
					        try {
					                Optional<File> ofaw1 = fs.open("a", Mode.READWRITEABLE);
					                File faw1 = ofaw1.get();
					                it("Process trying to open in write mode has been eventually unblocked (unblocked signal should be true)");
					                assertEquals(signal.flag, true);
					                fs.close(faw1);
						} catch (Exception e) {
					                // In case an exception happens
					                it("Process trying to open in write mode has been eventually unblocked (unblocked signal should be true)");
					                failure("");
					                e.printStackTrace();

						}
					}

				});
			        wt1.start();
			        try {
			                wt1.join(500);
				} catch (InterruptedException e) { failure("Interrupt"); }
			        // Detect that the thread is blocked
			        it("File open for reading; another process trying to open it for writing is blocked");
			        assertEquals(wt1.getState(), Thread.State.WAITING);

			        // Set observable checkpoint in other thread
			        signal.flag = true;
			        // Triggers unblock of wt1
			        fs.close(ofA.get());

			        try {
			                wt1.join(500);
				} catch (InterruptedException e) { failure("Interrupt"); }

			        it("Successfuly unblocked writing process");
			        assertEquals(wt1.getState(), Thread.State.TERMINATED);
			}
		});
		main.start();

		try {
			main.join(1000);
		} catch (InterruptedException e) { failure("Interrupt"); }

		it("Successfuly closed files after blocking interaction");
		assertEquals(main.getState(), Thread.State.TERMINATED);

	}

	public void testMultiThread2() {
		FileServer fs = newFileServer();
		fs.create("a", "coheed");
		fs.create("b", "cambria");

    /* Situation
       Main: Open b for Read
       sub1:  Open a for Write
            -- check success
             (WAIT on SEMAPHORE)
       sub2: Open a for Read
         -- check it gets block
         * kill this thread
       Main: Open b for Read (not blocked)
             Read b . Close b
       sub3: Open a for write
               -- check it gets blocked
       Main: signal sub1 to procedd
       sub1:  write "claudio" to a
              close a
       sub3 should get unblocked
              -- check unblocked
              -- get the file and read it 'claudio'
              -- write 'ambelina'
              -- close a
       -- check sub3 is not blocked and termintes
    */
		// Open b
		System.out.println("open b in READ mode");
		Optional<File> ofb = fs.open("b", Mode.READABLE);

		Semaphore signaller = new Semaphore(1);
		try {
			signaller.acquire();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}


		Signal doneOpen = new Signal();
		Signal signal = new Signal();

		Thread sub1 = new Thread(new Runnable() {

			@Override
			public void run() {
			        try {
			                Optional<File> ofa1 = fs.open("a", Mode.READWRITEABLE);
			                File fa1 = ofa1.get();
			                it("Process with write mode of file succeeds in reading while blocking others");
			                assertEquals(fa1.read(), "coheed");
			                doneOpen.flag = true;

			                // Wait
			                signaller.acquire();
			                fa1.write("claudio");
			                // Set observable checkpoint in other thread
			                signal.flag = true;
			                // Triggers unblock
			                fs.close(fa1);


				} catch (Exception e) {
			                failure("");
			                e.printStackTrace();
				}
			}
		});
		sub1.start();
		System.out.println("started sub1");


		// Wait and check that it succesfully opened
		try {
			sub1.join(500);
			System.out.println("joined sub1");
		} catch (InterruptedException e) { failure("Interrupt"); }
		it("Another process trying to read whilst another writes gets blocked. Successful open and read.");
		assertEquals(doneOpen.flag, true);


		// Start off another thread which gets blocked
		Thread sub2 = new Thread(new Runnable() {

			@Override
			public void run() {
			        Optional<File> ofaa = fs.open("a", Mode.READABLE);
			        File faa = ofaa.get();
			        // Eventually close
			        fs.close(faa);
			}

		});
		sub2.start();
		System.out.println("started sub2");
		try {
			sub2.join(500);
			System.out.println("joined sub2");
		} catch (InterruptedException e) { failure("Interrupt"); }

		System.out.println("checking sub2 thread state");

		// Detect that the thread is blocked
		assertEquals(sub2.getState(), Thread.State.WAITING);
		// Give up on the thread

		it("Another process trying to read a file (while another writes a different file) is not blocked");
		System.out.println("trying to open b in READ mode");
		Optional<File> ofb2 = fs.open("b", Mode.READABLE);
		System.out.println("opened b in READ mode");
		File fb2 = ofb2.get();
		assertEquals(fb2.read(), "cambria");
		fs.close(fb2);

		signal.flag = false;

		Thread sub3 = new Thread(new Runnable() {
			@Override
			public void run() {

			        try {
			                Optional<File> ofa2 = fs.open("a", Mode.READWRITEABLE);
			                // I'm unblocked!
			                it("Second write attempt gets unblocked");
			                assertEquals(signal.flag, true);

			                // Signalled to go (the signal will come after the
			                // other thread has closed)
			                File fa2 = ofa2.get();

			                it("Unlocked process sees write from previous locking write process");
			                try {
			                        assertEquals(fa2.read(), "claudio");
			                        fa2.write("ambelina");
			                        fs.close(fa2);
					} catch (Exception e) {
			                        failure("");
			                        e.printStackTrace();

					}
				} catch (Exception e) {
			                failure("");
			                e.printStackTrace();

				}

			}
		});
		sub3.start();
		System.out.println("started sub3");
		// Let a bit of time elapse to allow the thread to get blocked
		try {
			sub3.join(500);
			System.out.println("joined sub3");
		} catch (InterruptedException e) { failure("Interrupt"); }
		// Detect block
		it("Another process trying to write whilst another writes gets blocked");
		assertEquals(sub3.getState(), Thread.State.WAITING);

		// Make the sub1 close the file, unclocking sub3
		signaller.release();

		// Let a bit of time elapse to allow the thread to get unblocked
		try {
			sub3.join(500);
		} catch (InterruptedException e) { failure("Interrupt");  }
		// Detect block
		it("Second write attempt was indeed unblocked");
		assertEquals(sub3.getState(), Thread.State.TERMINATED);

		try {
			sub2.join(500);
		} catch (InterruptedException e) { failure("Interrupt"); }

		// Detect that the thread 2 was unblocked eventually
		it("Blocked reader thread was eventually unblocked");
		assertEquals(sub2.getState(), Thread.State.TERMINATED);

		it("Second write attempt gets unblocked, and now the file is closed");
		assertEquals(fs.fileStatus("a"), Mode.CLOSED);

		it("Two processed open 'b' for reading, but only one closed it, so it should still be marked as readable");
		assertEquals(fs.fileStatus("b"), Mode.READABLE);

		it("Second write change observed");
		Optional<File> fa4 = fs.open("a", Mode.READABLE);
		assertEquals(fa4.get().read(), "ambelina");

	}

	// ************ TEST HARNESS *************************************************


	public String className;

	public static final String ANSI_RED = "\u001B[31m\033[1m";
	public static final String ANSI_RESET = "\u001B[0m";
	public static final String ANSI_GREEN = "\u001B[32m\033[1m";
	public static final String ANSI_BLUE = "\u001B[34m\033[1m";

	private Integer testCount = 0;
	private Integer passedTests = 0;
	private String currentTestName = "";
	private boolean prevTestFailed = false;

	public static void main(String[] args) {

    System.out.println("CO661 - Assessment 1 - Test Suite v1.1");
		// First string provides that name of your FileServer class
		if (args.length < 1) {
			System.out.println("Please pass the name of your FileServer class as an argument");

		} else {
			// Ok
			String className = args[0];
			TestSuite ts = new TestSuite(className);
			ts.go();
		}
	}

	public TestSuite(String className) {
		this.className = className;
	}

	public FileServer newFileServer() {
		// Create a new JavaClassLoader
		ClassLoader classLoader = this.getClass().getClassLoader();
		// Load the target class using its binary name
		FileServer fs = null;

		try {
			Class loadedMyClass = classLoader.loadClass(className);
			//System.out.println("Loaded class name: " + loadedMyClass.getName());

			// Create a new instance from the loaded class
			Constructor constructor = loadedMyClass.getConstructor();
			Object myClassObject = constructor.newInstance();
			fs = (FileServer) myClassObject;

		} catch (ClassNotFoundException e) {

			System.out.println("Error: Could not find class " + className);
			System.exit(1);

		} catch (NoSuchMethodException e) {

			System.out.println("Error: " + className + " is missing its constructor.");
			System.exit(1);

		} catch (Exception e) {

			System.out.println("Error: " + className + " could not be instantiated.");
			System.exit(1);

		}

		return fs;

	}

	public void go() {
		System.out.println("Running tests.");
		tests();
		System.out.println("\n" + ANSI_BLUE + "Tests: " + testCount + ANSI_RESET);
		System.out.println(ANSI_GREEN + "Passed: " + passedTests +  ANSI_RESET);
		if (passedTests == testCount) {
			System.out.println("\nOk.");
		} else {
			System.out.println(ANSI_RED + "Failed: " + (testCount - passedTests) + ANSI_RESET);
		}
	}


	public void describe(String msg) {
		System.out.println("\n" + msg);
	}

	public void it(String msg) {
		this.currentTestName = msg;
	}

	// Messages
	public void success() {
		this.passedTests++;
		this.prevTestFailed = false;
		System.out.print(".");
	}

	public void failure(String msg) {
		if (!this.prevTestFailed) {
			System.out.print("\n");
		}
		System.out.println(ANSI_RED + currentTestName + ".\n\tFailed: " + msg + "\n" + ANSI_RESET);
		this.prevTestFailed = true;

	}

	// Assertion boilerplate
	public void assertEquals(String s1, String s2) {
		if (s1 == s2) {
			success();
		} else {
			failure("Expected " + s2 + " got " + s1);
		}
		this.testCount++;
	}

	// Assertion boilerplate
	public void assertEquals(String[] s1, String[] s2) {
		boolean eq = (s1.length == s2.length);
		for (int i = 0; i < s1.length; i++) {
			eq = eq & (s1[i] == s2[i]);
		}
		if (eq) {
			success();
		} else {
			failure("Expected " + s2 + " got " + s1);
		}
		this.testCount++;
	}

	public void assertEquals(int s1, int s2) {
		if (s1 == s2) {
			success();
		} else {
			failure("Expected " + s2 + " got " + s1);
		}
		this.testCount++;
	}

	public void assertEquals(boolean s1, boolean s2) {
		if (s1 == s2) {
			success();
		} else {
			failure("Expected " + s2 + " got " + s1);
		}
		this.testCount++;
	}

	public void assertEquals(Mode s1, Mode s2) {
		if (s1 == s2) {
			success();
		} else {
			failure("Expected " + s2 + " got " + s1);
		}
		this.testCount++;
	}

	public void assertEquals(Thread.State s1, Thread.State s2) {
		if (s1 == s2) {
			success();
		} else {
			failure("Expected " + s2 + " got " + s1);
		}
		this.testCount++;
	}
}

class Signal {
	public boolean flag;
}
