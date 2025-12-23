package typr.internal.analysis

/** Analyzed from postgres metadata for prepared statements through jdbc
  */
case class MetadataParameterColumn(
    isNullable: ParameterNullable,
    isSigned: Boolean,
    parameterMode: ParameterMode,
    parameterType: JdbcType,
    parameterTypeName: String,
    precision: Int,
    scale: Int
)
