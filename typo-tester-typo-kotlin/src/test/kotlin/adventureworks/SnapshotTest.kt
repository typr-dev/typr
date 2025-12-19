package adventureworks

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import typo.runtime.Fragment
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists

object SnapshotTest {
    private val gitDirName = ".git"
    private val cwd: Path = Path.of(System.getProperty("user.dir"))

    /**
     * IntelliJ and build tools use different working directories.
     * This will place us somewhere predictable.
     */
    val projectRoot: Path by lazy {
        fun lookUpwards(p: Path?): Path? {
            if (p == null) return null
            val hasGitDir = Files.list(p).anyMatch { it.fileName.toString() == gitDirName }
            return if (hasGitDir) p else lookUpwards(p.parent)
        }

        lookUpwards(cwd) ?: error("Cannot find root of repo (uses $gitDirName to determine where)")
    }

    val outFolder: Path = projectRoot.resolve("snapshot-tests/kotlin-sql")

    val isCi: Boolean = System.getenv("BUILD_NUMBER") != null || System.getenv("CI") != null

    private val gitLock = Any()

    fun compareFragment(testClassName: String, fragmentName: String, fragment: Fragment?) {
        fragment?.let { sql ->
            val str = sql.render().replace(Regex("\\{param[\\d]+}"), "?")
            writeAndCompare(outFolder.resolve("$testClassName/$fragmentName.sql"), str)
        }
    }

    fun writeAndCompare(filePath: Path, contents: String) {
        if (isCi) {
            if (filePath.exists()) {
                val existing = Files.readString(filePath)
                assertEquals(existing, contents)
            } else {
                fail("Expected $filePath to exist")
            }
        } else {
            Files.createDirectories(filePath.parent)
            Files.writeString(filePath, contents)
            synchronized(gitLock) {
                val pb = ProcessBuilder("git", "add", filePath.toString())
                pb.redirectErrorStream(true)
                val process = pb.start()
                process.waitFor()
            }
        }
    }
}
