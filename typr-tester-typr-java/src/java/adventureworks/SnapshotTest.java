package adventureworks;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import typr.runtime.Fragment;

/** Base class for snapshot testing SQL fragments. Port of Scala SnapshotTest trait. */
public abstract class SnapshotTest {
  private static final Path DOT_GIT = Path.of(".git");
  private static final Path CWD = Path.of(System.getProperty("user.dir"));
  private static final Object GIT_LOCK = new Object();

  protected static final Path PROJECT_ROOT = findProjectRoot();
  protected static final boolean IS_CI =
      System.getenv("BUILD_NUMBER") != null || System.getenv("CI") != null;

  protected Path outFolder() {
    return PROJECT_ROOT.resolve("snapshot-tests/java-sql");
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

  protected void compareFragment(String fragmentName, Optional<Fragment> fragmentOpt) {
    fragmentOpt.ifPresent(
        fragment -> {
          String str = fragment.render().replaceAll("\\{param[\\d]+\\}", "?");
          writeAndCompare(
              outFolder().resolve(getClass().getSimpleName() + "/" + fragmentName + ".sql"), str);
        });
  }

  protected void writeAndCompare(Path in, String contents) {
    if (IS_CI) {
      if (Files.exists(in)) {
        try {
          String existing = Files.readString(in);
          assertEquals(existing, contents);
        } catch (IOException e) {
          fail("Failed to read " + in + ": " + e.getMessage());
        }
      } else {
        fail("Expected " + in + " to exist");
      }
    } else {
      try {
        Files.createDirectories(in.getParent());
        Files.writeString(in, contents);
        synchronized (GIT_LOCK) {
          new ProcessBuilder("git", "add", in.toString()).inheritIO().start().waitFor();
        }
      } catch (IOException | InterruptedException e) {
        fail("Failed to write " + in + ": " + e.getMessage());
      }
    }
  }
}
