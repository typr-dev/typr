package dev.typr.testutils;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.Callable;

/**
 * Utilities for snapshot testing with proper file locking for parallel test execution.
 *
 * <p>When tests run in parallel across multiple JVMs, git operations can conflict. This class
 * provides file-based locking to ensure exclusive access to git operations.
 */
public final class SnapshotTestUtils {

  private static final Path DOT_GIT = Path.of(".git");
  private static final Path CWD = Path.of(System.getProperty("user.dir"));
  private static final Path PROJECT_ROOT = findProjectRoot();
  private static final Path LOCK_FILE = PROJECT_ROOT.resolve(".snapshot-test.lock");
  private static final boolean IS_CI =
      System.getenv("BUILD_NUMBER") != null || System.getenv("CI") != null;

  private SnapshotTestUtils() {}

  /** Returns the project root directory (containing .git). */
  public static Path projectRoot() {
    return PROJECT_ROOT;
  }

  /** Returns true if running in CI environment. */
  public static boolean isCi() {
    return IS_CI;
  }

  /**
   * Execute a callable while holding an exclusive file lock. This ensures only one process at a
   * time can execute git operations.
   */
  public static <T> T withGitLock(Callable<T> action) {
    try (FileChannel channel =
        FileChannel.open(LOCK_FILE, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
      FileLock lock = channel.lock();
      try {
        return action.call();
      } finally {
        lock.release();
      }
    } catch (Exception e) {
      throw new RuntimeException("Failed to execute with git lock", e);
    }
  }

  /** Execute a runnable while holding an exclusive file lock. */
  public static void withGitLock(Runnable action) {
    withGitLock(
        () -> {
          action.run();
          return null;
        });
  }

  /**
   * Write content to a file and git add it (when not in CI). In CI, verify the file matches
   * expected content.
   *
   * @param path the file path to write to
   * @param contents the content to write
   * @throws AssertionError if in CI and content doesn't match
   */
  public static void writeAndCompare(Path path, String contents) {
    if (IS_CI) {
      if (Files.exists(path)) {
        try {
          String existing = Files.readString(path);
          if (!existing.equals(contents)) {
            throw new AssertionError(
                "Snapshot mismatch for "
                    + path
                    + "\nExpected:\n"
                    + contents
                    + "\nActual:\n"
                    + existing);
          }
        } catch (IOException e) {
          throw new RuntimeException("Failed to read " + path, e);
        }
      } else {
        throw new AssertionError("Expected " + path + " to exist");
      }
    } else {
      try {
        Files.createDirectories(path.getParent());
        Files.writeString(path, contents);
      } catch (IOException e) {
        throw new RuntimeException("Failed to write " + path, e);
      }
      withGitLock(
          () -> {
            try {
              new ProcessBuilder("git", "add", path.toString()).inheritIO().start().waitFor();
            } catch (IOException | InterruptedException e) {
              throw new RuntimeException("Failed to git add " + path, e);
            }
          });
    }
  }

  private static Path findProjectRoot() {
    Path p = CWD;
    while (p != null) {
      try (var stream = Files.list(p)) {
        if (stream.anyMatch(path -> path.getFileName().equals(DOT_GIT))) {
          return p;
        }
      } catch (IOException e) {
        // ignore
      }
      p = p.getParent();
    }
    throw new RuntimeException(
        "Cannot find root of repo (uses " + DOT_GIT + " to determine where)");
  }
}
