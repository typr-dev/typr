package typr.cli.beta

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path, Paths, StandardOpenOption}
import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate}

/** What we persist about a single acceptance. Plain text on disk, human-inspectable; if a key fails to parse the whole file is treated as absent (re-prompt).
  */
final case class Acceptance(acceptedAt: Instant, binaryVersion: String, termsVersion: Int)

/** Read / write the beta-acceptance file, and answer the time-based queries every gate site needs. All paths are XDG-aware: `$XDG_CONFIG_HOME/typr/beta-accepted.txt`, falling back to
  * `~/.config/typr/beta-accepted.txt` on Linux/Mac, `%APPDATA%/typr/beta-accepted.txt` on Windows. No locking — the file is tiny, writes are last-wins, and the user only edits it indirectly via
  * `--accept` or the TUI modal.
  */
object BetaGate {

  // ─────────────────────────────────── file location ───────────────────────────────────

  /** Resolved acceptance-file path. Created on demand by [[markAccepted]]. */
  def configPath: Path = configDir.resolve("beta-accepted.txt")

  private def configDir: Path = {
    val xdg = Option(System.getenv("XDG_CONFIG_HOME")).filter(_.nonEmpty)
    val appdata = Option(System.getenv("APPDATA")).filter(_.nonEmpty)
    val home = System.getProperty("user.home")
    val base =
      xdg
        .map(Paths.get(_))
        .orElse(appdata.map(Paths.get(_)))
        .getOrElse(Paths.get(home, ".config"))
    base.resolve("typr")
  }

  // ─────────────────────────────────── read / write ───────────────────────────────────

  /** Parse the acceptance file if present and well-formed. A missing file, an I/O error, or any unparseable key returns `None` — the caller treats that as "not accepted, prompt again".
    */
  def read(): Option[Acceptance] =
    try {
      val p = configPath
      if (!Files.exists(p)) None
      else {
        val kvs = Files
          .readAllLines(p, StandardCharsets.UTF_8)
          .toArray(Array.empty[String])
          .iterator
          .map(_.trim)
          .filterNot(_.isEmpty)
          .filterNot(_.startsWith("#"))
          .flatMap { line =>
            line.split(":", 2) match {
              case Array(k, v) => Some(k.trim -> v.trim)
              case _           => None
            }
          }
          .toMap
        for {
          rawAt <- kvs.get("accepted-at")
          at <- tryParseInstant(rawAt)
          ver <- kvs.get("binary-version")
          rawTv <- kvs.get("terms-version")
          tv <- rawTv.toIntOption
        } yield Acceptance(at, ver, tv)
      }
    } catch case _: Throwable => None

  /** Write the acceptance file, creating any missing parent dirs. Best-effort — failures throw (caller can surface a stderr message). The file's contents are stable across re-acceptances apart from
    * `accepted-at`, so we always overwrite rather than append.
    */
  def markAccepted(binaryVersion: String): Unit = {
    val now = Instant.now()
    val body =
      s"""accepted-at: ${DateTimeFormatter.ISO_INSTANT.format(now)}
         |binary-version: $binaryVersion
         |terms-version: ${BetaTerms.termsVersion}
         |""".stripMargin
    Files.createDirectories(configDir)
    Files.write(
      configPath,
      body.getBytes(StandardCharsets.UTF_8),
      StandardOpenOption.CREATE,
      StandardOpenOption.TRUNCATE_EXISTING,
      StandardOpenOption.WRITE
    )
    ()
  }

  // ─────────────────────────────────── queries ───────────────────────────────────

  /** Whether the stored acceptance covers the current terms version. */
  def isCurrent: Boolean = read().exists(_.termsVersion >= BetaTerms.termsVersion)

  /** Days until expiry. Negative once expired (e.g. `-3` three days after `expiresOn`). */
  def daysUntilExpiry(today: LocalDate = LocalDate.now()): Long =
    today.until(BetaTerms.expiresOn, java.time.temporal.ChronoUnit.DAYS)

  def isExpired(today: LocalDate = LocalDate.now()): Boolean = daysUntilExpiry(today) < 0

  def isInWarningWindow(today: LocalDate = LocalDate.now()): Boolean = {
    val d = daysUntilExpiry(today)
    d >= 0 && d <= BetaTerms.warningWindowDays
  }

  // ─────────────────────────────────── messages ───────────────────────────────────

  /** The stderr block printed when an expired binary is invoked. Mirrors the TUI expired screen so users see the same text wherever they bump into the wall.
    */
  val expiredMessage: String =
    s"""This Typr build expired on ${BetaTerms.expiresOn}.
       |
       |Update to keep using Typr:
       |  download the latest from https://typr.dev/get
       |
       |Closed-beta builds are time-limited so we can keep everyone on a
       |recent version while the API stabilizes. Apologies for the friction.
       |""".stripMargin

  /** One-line CLI warning when within the warning window. Goes through the structured logger so it lands in CI logs and gives a paper trail.
    */
  def warningLine(today: LocalDate = LocalDate.now()): String = {
    val d = daysUntilExpiry(today)
    val units = if (d == 1) "day" else "days"
    s"closed beta · this build expires in $d $units — download a fresh one at https://typr.dev/get"
  }

  /** Stderr refusal printed for a CLI command run before acceptance. */
  val notAcceptedMessage: String =
    s"""Typr is in closed beta. By running it you accept the terms.
       |Re-run with --accept to confirm, or run `typr terms` to read them.
       |""".stripMargin

  // ─────────────────────────────────── helpers ───────────────────────────────────

  private def tryParseInstant(s: String): Option[Instant] =
    try Some(Instant.parse(s))
    catch case _: Throwable => None
}
