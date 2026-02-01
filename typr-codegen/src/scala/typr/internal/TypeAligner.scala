package typr
package internal

/** Aligns TypeDefinitions across multiple sources (databases, OpenAPI) to create unified shared types.
  *
  * When generating from multiple sources, TypeAligner:
  *   1. Collects scan results from each source 2. Finds TypeEntries that match across multiple sources 3. Determines the canonical db type for each entry 4. Creates alignment info for generating
  *      unified wrapper types
  *
  * Entries that only match in one source keep their original type (no alignment needed).
  */
object TypeAligner {

  /** Represents the kind of source a type alignment comes from. */
  sealed trait SourceKind
  object SourceKind {
    case class Database(dbType: DbType) extends SourceKind
    case class OpenApi(specName: String) extends SourceKind
  }

  /** Result of aligning a TypeEntry across multiple sources. */
  case class AlignedEntry(
      entry: TypeEntry,
      canonicalDbType: db.Type,
      sourceAlignments: List[SourceAlignment]
  )

  /** Entry that matched in only one source - keeps original behavior. */
  case class SingleSourceEntry(
      entry: TypeEntry,
      sourceName: String,
      sourceKind: SourceKind,
      compatResult: TypeCompatibilityChecker.CheckResult.Compatible
  )

  /** How a single source aligns to the canonical type. */
  case class SourceAlignment(
      sourceName: String,
      sourceKind: SourceKind,
      sampleDbType: db.Type,
      transform: TypoType.AlignmentTransform,
      locations: List[String]
  )

  /** Result from aligning across sources. */
  case class AlignmentResult(
      /** Entries successfully aligned across multiple sources */
      alignedEntries: List[AlignedEntry],
      /** Entries that only matched in one source - use original type */
      singleSourceEntries: List[SingleSourceEntry],
      /** Warnings about alignment issues (not errors - generation continues) */
      warnings: List[String],
      /** Errors that prevent generation */
      errors: List[String]
  )

  /** Align TypeDefinitions across multiple source scan results.
    *
    * @param typeDefinitions
    *   The shared TypeDefinitions
    * @param dbScanResults
    *   Scan results from database sources, keyed by source name and DbType
    * @param apiScanResults
    *   Scan results from OpenAPI sources, keyed by source name
    * @return
    *   AlignmentResult with aligned entries, single-source entries, and any issues
    */
  def align(
      typeDefinitions: TypeDefinitions,
      dbScanResults: List[(String, DbType, TypeMatcher.ScanResult)],
      apiScanResults: List[(String, TypeMatcher.OpenApiScanResult)] = Nil
  ): AlignmentResult = {
    // Group compatible results by entry name across all sources
    val dbCompatible: List[(String, (String, SourceKind, TypeCompatibilityChecker.CheckResult.Compatible))] =
      dbScanResults.flatMap { case (sourceName, dbType, scanResult) =>
        scanResult.compatibilityResults.collect { case c: TypeCompatibilityChecker.CheckResult.Compatible =>
          (c.entry.name, (sourceName, SourceKind.Database(dbType), c))
        }
      }

    val apiCompatible: List[(String, (String, SourceKind, TypeCompatibilityChecker.CheckResult.Compatible))] =
      apiScanResults.flatMap { case (sourceName, scanResult) =>
        scanResult.compatibilityResults.collect { case c: TypeCompatibilityChecker.CheckResult.Compatible =>
          (c.entry.name, (sourceName, SourceKind.OpenApi(sourceName), c))
        }
      }

    val compatibleByEntry: Map[String, List[(String, SourceKind, TypeCompatibilityChecker.CheckResult.Compatible)]] =
      (dbCompatible ++ apiCompatible)
        .groupBy(_._1)
        .map { case (k, v) => k -> v.map(_._2) }

    val alignedEntries = List.newBuilder[AlignedEntry]
    val singleSourceEntries = List.newBuilder[SingleSourceEntry]
    val warnings = List.newBuilder[String]

    // Process each TypeEntry
    typeDefinitions.entries.foreach { entry =>
      compatibleByEntry.get(entry.name) match {
        case Some(sources) if sources.size > 1 =>
          // Multiple sources - try to align
          alignMultipleSources(entry, sources) match {
            case Right(aligned) =>
              alignedEntries += aligned
            case Left(warning) =>
              warnings += warning
              // Fall back to single-source entries
              sources.foreach { case (sourceName, sourceKind, compatResult) =>
                singleSourceEntries += SingleSourceEntry(entry, sourceName, sourceKind, compatResult)
              }
          }

        case Some(sources) if sources.size == 1 =>
          // Single source - keep original type
          val (sourceName, sourceKind, compatResult) = sources.head
          singleSourceEntries += SingleSourceEntry(entry, sourceName, sourceKind, compatResult)

        case _ =>
          // No matches - nothing to do
          ()
      }
    }

    // Collect errors from all sources
    val dbErrors = dbScanResults.flatMap { case (sourceName, _, scanResult) =>
      scanResult.errors.map(e => s"[$sourceName] $e")
    }
    val apiErrors = apiScanResults.flatMap { case (sourceName, scanResult) =>
      scanResult.errors.map(e => s"[$sourceName] $e")
    }
    val allErrors = dbErrors ++ apiErrors

    AlignmentResult(
      alignedEntries = alignedEntries.result(),
      singleSourceEntries = singleSourceEntries.result(),
      warnings = warnings.result(),
      errors = allErrors
    )
  }

  /** Try to align an entry across multiple sources.
    *
    * @return
    *   Right(AlignedEntry) if successful, Left(warning message) if alignment failed
    */
  private def alignMultipleSources(
      entry: TypeEntry,
      sources: List[(String, SourceKind, TypeCompatibilityChecker.CheckResult.Compatible)]
  ): Either[String, AlignedEntry] = {
    // Check that all sources have compatible canonical types
    val canonicalTypes = sources.map { case (_, _, c) => c.canonicalType }.distinct
    val compatibilityClasses = canonicalTypes.map(TypeCompatibilityChecker.compatibilityClass).distinct

    if (compatibilityClasses.size > 1) {
      // Different compatibility classes - can't align
      val details = sources
        .map { case (name, _, c) =>
          s"$name: ${TypeCompatibilityChecker.compatibilityClass(c.canonicalType).description}"
        }
        .mkString(", ")
      Left(s"Type '${entry.name}' has incompatible canonical types across sources: $details")
    } else {
      // All compatible - use the first source's canonical type
      val (_, _, firstResult) = sources.head
      val canonicalDbType = firstResult.canonicalType

      val sourceAlignments = sources.map { case (sourceName, sourceKind, compatResult) =>
        // Get a sample db type from the matches - this tells us the actual source type
        val sampleDbType = compatResult.matches.headOption.map(_.dbType).getOrElse(compatResult.canonicalType)

        // Determine if this is a domain/wrapper type that needs unwrapping
        val transform = determineTransform(sampleDbType, compatResult.canonicalType)

        // Build location strings for documentation
        val locations = compatResult.matches.map { m =>
          m.source match {
            case TypeMatcher.MatchSource.DbColumn(_, schema, table, col) =>
              if (schema.isEmpty) s"$table.$col" else s"$schema.$table.$col"
            case TypeMatcher.MatchSource.ModelField(spec, schema, field) =>
              s"$spec:$schema.$field"
            case TypeMatcher.MatchSource.ApiParam(spec, path, param, _) =>
              s"$spec:$path.$param"
          }
        }

        SourceAlignment(
          sourceName = sourceName,
          sourceKind = sourceKind,
          sampleDbType = sampleDbType,
          transform = transform,
          locations = locations
        )
      }

      Right(AlignedEntry(entry, canonicalDbType, sourceAlignments))
    }
  }

  /** Create a TypoType.Aligned for a specific source from an AlignedEntry.
    *
    * @param entry
    *   The aligned entry
    * @param sourceAlignment
    *   The specific source's alignment
    * @param canonicalJvmType
    *   The canonical JVM type (computed by caller using TypeMapper)
    * @param sourceTypoType
    *   The TypoType from the source (could be Generated for domain, Standard for raw type)
    */
  def createAlignedTypoType(
      entry: AlignedEntry,
      sourceAlignment: SourceAlignment,
      canonicalJvmType: jvm.Type,
      sourceTypoType: TypoType
  ): TypoType.Aligned = {
    val alignments = entry.sourceAlignments.map { sa =>
      TypoType.TypeAlignment(
        sourceKind = sa.sourceKind,
        dbType = sa.sampleDbType,
        locations = sa.locations
      )
    }

    TypoType.Aligned(
      jvmType = canonicalJvmType,
      sourceType = sourceTypoType,
      transform = sourceAlignment.transform,
      alignments = alignments
    )
  }

  /** Determine the alignment transform based on source and canonical types.
    *
    * If the source type is a domain/wrapper around the canonical type, we need Unpack. If the source type IS the canonical type, we use Direct.
    */
  private def determineTransform(sampleDbType: db.Type, @annotation.nowarn canonicalType: db.Type): TypoType.AlignmentTransform =
    sampleDbType match {
      // PostgreSQL domain reference - needs unwrapping
      case db.PgType.DomainRef(_, _, _) =>
        TypoType.AlignmentTransform.Unpack

      // SQL Server alias type reference - needs unwrapping
      case db.SqlServerType.AliasTypeRef(_, _, _, _) =>
        TypoType.AlignmentTransform.Unpack

      // DB2 distinct type - needs unwrapping
      case db.DB2Type.DistinctType(_, _) =>
        TypoType.AlignmentTransform.Unpack

      // Same type or compatible base type - direct mapping
      case _ =>
        TypoType.AlignmentTransform.Direct
    }
}
