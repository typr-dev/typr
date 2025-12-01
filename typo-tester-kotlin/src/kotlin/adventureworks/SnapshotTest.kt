package adventureworks

import typo.runtime.Fragment
import org.junit.jupiter.api.Assertions.*
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.Optional

/**
 * Base class for snapshot testing SQL fragments.
 */
abstract class SnapshotTest {
    companion object {
        private val DOT_GIT = Path.of(".git")
        private val CWD = Path.of(System.getProperty("user.dir"))
        private val GIT_LOCK = Any()

        val PROJECT_ROOT: Path = findProjectRoot()
        val IS_CI: Boolean = System.getenv("BUILD_NUMBER") != null || System.getenv("CI") != null

        private fun findProjectRoot(): Path {
            var p: Path? = CWD
            while (p != null) {
                try {
                    Files.list(p).use { stream ->
                        if (stream.anyMatch { path -> path.fileName == DOT_GIT }) {
                            return p
                        }
                    }
                } catch (e: IOException) {
                    // ignore
                }
                p = p.parent
            }
            throw RuntimeException("Cannot find root of repo (uses $DOT_GIT to determine where)")
        }
    }

    protected open fun outFolder(): Path {
        return PROJECT_ROOT.resolve("snapshot-tests/kotlin-sql")
    }

    protected fun compareFragment(fragmentName: String, fragmentOpt: Optional<Fragment>) {
        fragmentOpt.ifPresent { fragment ->
            val str = fragment.render().replace(Regex("\\{param[\\d]+\\}"), "?")
            writeAndCompare(outFolder().resolve("${javaClass.simpleName}/$fragmentName.sql"), str)
        }
    }

    protected fun writeAndCompare(path: Path, contents: String) {
        if (IS_CI) {
            if (Files.exists(path)) {
                try {
                    val existing = Files.readString(path)
                    assertEquals(existing, contents)
                } catch (e: IOException) {
                    fail("Failed to read $path: ${e.message}")
                }
            } else {
                fail("Expected $path to exist")
            }
        } else {
            try {
                Files.createDirectories(path.parent)
                Files.writeString(path, contents)
                synchronized(GIT_LOCK) {
                    ProcessBuilder("git", "add", path.toString())
                        .inheritIO()
                        .start()
                        .waitFor()
                }
            } catch (e: IOException) {
                fail("Failed to write $path: ${e.message}")
            } catch (e: InterruptedException) {
                fail("Failed to write $path: ${e.message}")
            }
        }
    }
}
