/**
 * Dynamic file resolution for showcase-generated code using webpack require.context
 *
 * This allows any file to be loaded by path without maintaining a curated list.
 */

// Create webpack contexts for each language
// require.context(directory, useSubdirectories, regExp)
const javaContext = require.context(
  '../../showcase-generated',
  true,
  /\.java$/
);

const kotlinContext = require.context(
  '../../showcase-generated',
  true,
  /\.kt$/
);

const scalaContext = require.context(
  '../../showcase-generated',
  true,
  /\.scala$/
);

// Usage examples contexts (checked-in code examples for documentation)
const javaExamplesContext = require.context(
  '../../usage-examples',
  true,
  /\.java$/
);

const kotlinExamplesContext = require.context(
  '../../usage-examples',
  true,
  /\.kt$/
);

const scalaExamplesContext = require.context(
  '../../usage-examples',
  true,
  /\.scala$/
);

const LANG_CONTEXTS = {
  java: javaContext,
  kotlin: kotlinContext,
  scala: scalaContext,
};

const EXAMPLES_CONTEXTS = {
  java: javaExamplesContext,
  kotlin: kotlinExamplesContext,
  scala: scalaExamplesContext,
};

const LANG_EXTENSIONS = {
  java: '.java',
  kotlin: '.kt',
  scala: '.scala',
};

export const DATABASES = ['postgres', 'mariadb', 'duckdb', 'oracle', 'sqlserver', 'db2', 'sqlite'];
export const LANGUAGES = ['java', 'kotlin', 'scala'];

export const DATABASE_LABELS = {
  postgres: 'PostgreSQL',
  mariadb: 'MariaDB',
  duckdb: 'DuckDB',
  oracle: 'Oracle',
  sqlserver: 'SQL Server',
  db2: 'DB2',
  sqlite: 'SQLite',
};

export const LANGUAGE_LABELS = {
  java: 'Java',
  kotlin: 'Kotlin',
  scala: 'Scala',
};

/**
 * Strip the auto-generated header comment from file content
 */
function stripHeader(content) {
  const lines = content.split('\n');
  let startIdx = 0;

  // Skip initial comment block
  if (lines[0]?.startsWith('/**') || lines[0]?.startsWith('/*')) {
    for (let i = 0; i < lines.length; i++) {
      if (lines[i].includes('*/')) {
        startIdx = i + 1;
        break;
      }
    }
  }

  // Skip empty lines after header
  while (startIdx < lines.length && lines[startIdx].trim() === '') {
    startIdx++;
  }

  return lines.slice(startIdx).join('\n');
}

/**
 * Get file content for a given database, language, and file path.
 *
 * @param {string} database - e.g., 'postgres'
 * @param {string} language - e.g., 'java', 'kotlin', 'scala'
 * @param {string} filePath - e.g., 'company/CompanyId' (without extension, relative to showcase schema)
 *                            or 'customtypes/Defaulted' for package-level types
 * @returns {string|null} - File content or null if not found
 */
export function getFile(database, language, filePath) {
  const context = LANG_CONTEXTS[language];
  if (!context) return null;

  const ext = LANG_EXTENSIONS[language];

  // Try showcase/showcase/ first (schema-level: tables, views)
  // Then try showcase/ (package-level: customtypes)
  const paths = [
    `./${database}/${language}/showcase/showcase/${filePath}${ext}`,
    `./${database}/${language}/showcase/${filePath}${ext}`,
  ];

  for (const fullPath of paths) {
    try {
      const content = context(fullPath);
      // Handle both default exports and raw content
      const raw = typeof content === 'string' ? content : content.default || content;
      return stripHeader(raw);
    } catch (e) {
      // Try next path
    }
  }

  return null;
}

/**
 * Check if a file exists for the given combination
 */
export function hasFile(database, language, filePath) {
  return getFile(database, language, filePath) !== null;
}

/**
 * Get usage example file content for a given database, language, and file path.
 *
 * @param {string} database - e.g., 'postgres'
 * @param {string} language - e.g., 'java', 'kotlin', 'scala'
 * @param {string} filePath - e.g., 'TestInsertExample' (without extension)
 *                            or 'patterns/MultiRepoExample' for database-agnostic patterns
 * @returns {string|null} - File content or null if not found
 */
export function getExampleFile(database, language, filePath) {
  const context = EXAMPLES_CONTEXTS[language];
  if (!context) return null;

  const ext = LANG_EXTENSIONS[language];

  // Try database-specific path first, then database-agnostic path
  const paths = [
    `./${database}/${language}/${filePath}${ext}`,
    `./${filePath}${ext}`,  // for patterns/ and other database-agnostic examples
  ];

  for (const fullPath of paths) {
    try {
      const content = context(fullPath);
      const raw = typeof content === 'string' ? content : content.default || content;
      return stripHeader(raw);
    } catch (e) {
      // Try next path
    }
  }

  return null;
}

/**
 * Get list of available database/language combinations for a file
 */
export function getAvailableCombinations(filePath) {
  const result = [];
  for (const db of DATABASES) {
    for (const lang of LANGUAGES) {
      if (hasFile(db, lang, filePath)) {
        result.push({ database: db, language: lang });
      }
    }
  }
  return result;
}
