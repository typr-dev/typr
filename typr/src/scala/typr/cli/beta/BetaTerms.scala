package typr.cli.beta

import java.time.LocalDate

/** The hard-coded terms of the current closed-beta release.
  *
  * `termsVersion` is the lever that re-prompts existing users when material text changes (cosmetic edits should leave the version alone). On each run we compare this number against the one in the
  * user's [[Acceptance]] file — anything stored under a lower version triggers a fresh prompt.
  *
  * `expiresOn` is the literal date the binary refuses to run after. There is no auto-update — users grab a fresh build from typr.dev. Keep `warningDays` in step with how often we publish: shorter
  * than the release cadence and users get caught surprised; much longer and they tune out the warning.
  */
object BetaTerms {

  /** Bump when the text of [[displayText]] changes substantively — adds a new bullet, alters pricing/licensing assertions, etc. Existing acceptances at a lower version re-prompt.
    */
  val termsVersion: Int = 1

  /** This particular build refuses to run after this date (inclusive — `LocalDate.now()` > `expiresOn` is the expired check). Users update by downloading a fresh binary.
    */
  val expiresOn: LocalDate = LocalDate.of(2026, 7, 1)

  /** How many days before expiry we start warning loudly in CLI logs and the TUI splash chip. */
  val warningWindowDays: Int = 14

  /** Plain-text terms — rendered as-is in the TUI BetaNotice screen and on `typr terms`. Wrap lines at ~70 cols for readability inside the modal box.
    */
  val displayText: String =
    """Typr Closed Beta — Terms
      |
      |  • Typr is not open source. The codegen and runtime are
      |    proprietary; the code Typr emits is yours to ship.
      |
      |  • Support for commercial databases (Oracle, SQL Server, DB2)
      |    will not remain free in the released product. Open-source
      |    databases — Postgres, MariaDB/MySQL, DuckDB — are expected
      |    to stay free for reasonable use.
      |
      |  • There will be a limit on how many boundaries can take part
      |    in domain-type alignment per generate run. Small projects
      |    will be unaffected; large multi-source generations will
      |    eventually require a paid tier.
      |
      |  • This particular build expires on 2026-07-01. We're moving
      |    fast — keep your install fresh by re-downloading from
      |    typr.dev when prompted.
      |""".stripMargin

  /** A one-line summary for CLI output where the full text would be too much. */
  val shortLine: String =
    "Typr is in closed beta. Not open source; commercial DB support won't stay free; this build expires 2026-07-01."
}
