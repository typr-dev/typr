package typo
package internal

import typo.internal.codegen.{DbLib, JsonLib}

case class InternalOptions(
    dbLib: Option[DbLib],
    lang: Lang,
    debugTypes: Boolean,
    enableDsl: Boolean,
    enableFieldValue: Selector,
    enableStreamingInserts: Boolean,
    enableTestInserts: Selector,
    fileHeader: String,
    generateMockRepos: Selector,
    enablePrimaryKeyType: Selector,
    jsonLibs: List[JsonLib],
    keepDependencies: Boolean,
    logger: TypoLogger,
    naming: Naming,
    pkg: jvm.QIdent,
    readonlyRepo: Selector,
    typeOverride: TypeOverride
)
