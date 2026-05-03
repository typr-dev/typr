/**
 * ShowcaseSnippet - Displays code snippets from showcase-generated with segment extraction
 *
 * This component:
 * - Shows a database/language picker that syncs across all snippets
 * - Extracts segments between from/to markers (can be language-specific)
 * - FAILS HARD if segment markers are not found (no fallbacks)
 * - Dynamically resolves files from showcase-generated
 */

import React from 'react';
import CodeBlock from '@theme/CodeBlock';
import { useStack, DATABASES, LANGUAGES, DATABASE_LABELS, LANGUAGE_LABELS } from '../../context/StackContext';
import { getFile } from '../../data/showcaseFiles';
import styles from './styles.module.css';

// Map language to Prism language for syntax highlighting
const PRISM_LANGUAGES = {
  java: 'java',
  kotlin: 'kotlin',
  scala: 'scala',
};

/**
 * Skip package and import statements from code.
 * Returns the code starting after imports (and any blank lines following imports).
 */
function skipImports(code) {
  const lines = code.split('\n');
  let startIdx = 0;

  // Skip package statement
  if (lines[startIdx]?.trim().startsWith('package ')) {
    startIdx++;
  }

  // Skip blank lines after package
  while (startIdx < lines.length && lines[startIdx].trim() === '') {
    startIdx++;
  }

  // Skip import statements
  while (startIdx < lines.length && lines[startIdx].trim().startsWith('import ')) {
    startIdx++;
  }

  // Skip blank lines after imports
  while (startIdx < lines.length && lines[startIdx].trim() === '') {
    startIdx++;
  }

  return lines.slice(startIdx).join('\n');
}

/**
 * Extract a segment from code between 'from' and 'to' markers.
 * - If 'to' marker found: returns lines from start up to (but NOT including) the 'to' line
 * - If 'from' marker found: returns lines starting from (and including) the 'from' line
 *
 * @param {string} code - The full code
 * @param {string} from - Start marker (line containing this text, inclusive)
 * @param {string} to - End marker (line containing this text, exclusive)
 * @returns {string} - The extracted segment
 * @throws {Error} - If markers are not found
 */
function extractSegment(code, from, to) {
  if (!from && !to) {
    return code;
  }

  const lines = code.split('\n');
  let startIdx = 0;
  let endIdx = lines.length;

  if (from) {
    startIdx = lines.findIndex(line => line.includes(from));
    if (startIdx === -1) {
      throw new Error(`Segment start marker not found: "${from}"`);
    }
  }

  if (to) {
    // Search from startIdx onwards, exclusive (don't include the 'to' line)
    const toIdx = lines.findIndex((line, idx) => idx > startIdx && line.includes(to));
    if (toIdx === -1) {
      throw new Error(`Segment end marker not found: "${to}"`);
    }
    endIdx = toIdx;
  }

  return lines.slice(startIdx, endIdx).join('\n');
}

/**
 * Resolve segment markers for a specific language.
 * Language-specific markers (fromByLang/toByLang) take precedence over generic from/to.
 */
function resolveSegmentMarkers(language, from, to, fromByLang, toByLang) {
  const resolvedFrom = fromByLang?.[language] ?? from ?? null;
  const resolvedTo = toByLang?.[language] ?? to ?? null;
  return { from: resolvedFrom, to: resolvedTo };
}

/**
 * Database/Language picker bar with dropdowns
 * @param {Object} props
 * @param {string[]} [props.databases] - Optional list of databases to show (defaults to all)
 * @param {boolean} [props.showFullFile] - Current show full file state (per-snippet)
 * @param {function} [props.onShowFullFileChange] - Callback for show full file toggle
 * @param {string} [props.fileName] - File name to display
 */
function StackPicker({ databases, showFullFile, onShowFullFileChange, fileName }) {
  const { database, language, setDatabase, setLanguage } = useStack();

  // Filter databases if specified
  const availableDatabases = databases || DATABASES;

  return (
    <div className={styles.pickerBar}>
      {fileName && <span className={styles.fileName}>{fileName}</span>}
      <div className={styles.pickerGroup}>
        <select
          className={styles.pickerSelect}
          value={database}
          onChange={(e) => setDatabase(e.target.value)}
          disabled={availableDatabases.length === 1}
        >
          {availableDatabases.map(db => (
            <option key={db} value={db}>{DATABASE_LABELS[db]}</option>
          ))}
        </select>
      </div>
      <div className={styles.pickerGroup}>
        <select
          className={styles.pickerSelect}
          value={language}
          onChange={(e) => setLanguage(e.target.value)}
        >
          {LANGUAGES.map(lang => (
            <option key={lang} value={lang}>{LANGUAGE_LABELS[lang]}</option>
          ))}
        </select>
      </div>
      {onShowFullFileChange && (
        <label className={styles.fullFileCheckbox}>
          <input
            type="checkbox"
            checked={showFullFile}
            onChange={(e) => onShowFullFileChange(e.target.checked)}
          />
          <span>Full file</span>
        </label>
      )}
    </div>
  );
}

/**
 * ShowcaseSnippet - Main component
 *
 * @param {Object} props
 * @param {string} props.file - File path without extension (e.g., "showcase/company/CompanyId")
 * @param {string} [props.from] - Start marker for segment extraction (line containing this text, inclusive)
 * @param {string} [props.to] - End marker for segment extraction (line containing this text, exclusive)
 * @param {Object} [props.fromByLang] - Language-specific start markers { java: "...", kotlin: "...", scala: "..." }
 * @param {Object} [props.toByLang] - Language-specific end markers { java: "...", kotlin: "...", scala: "..." }
 * @param {string} [props.title] - Optional title
 * @param {boolean} [props.showPicker=true] - Whether to show the db/lang picker
 * @param {string[]} [props.databases] - Optional list of databases to show (e.g., ["postgres"] for PostgreSQL-only features)
 */
export default function ShowcaseSnippet({ file, from, to, fromByLang, toByLang, title, showPicker = true, databases }) {
  const { database, language, languageLabel } = useStack();
  const [showFullFile, setShowFullFile] = React.useState(false);

  // If databases are restricted and current selection isn't allowed, use first allowed database
  const effectiveDatabase = databases && !databases.includes(database)
    ? databases[0]
    : database;
  const effectiveDatabaseLabel = DATABASE_LABELS[effectiveDatabase];

  const fullCode = getFile(effectiveDatabase, language, file);

  // FAIL HARD if file not found
  if (!fullCode) {
    throw new Error(
      `ShowcaseSnippet: File "${file}" not found for ${effectiveDatabase}/${language}. ` +
      `Check that the file exists in showcase-generated/${effectiveDatabase}/${language}/${file}.*`
    );
  }

  // Resolve language-specific segment markers
  const { from: resolvedFrom, to: resolvedTo } = resolveSegmentMarkers(
    language, from, to, fromByLang, toByLang
  );

  // Build the snippet: apply segment extraction, then optionally skip imports
  let snippet;
  if (showFullFile) {
    // Show the entire file as-is
    snippet = fullCode;
  } else {
    try {
      // First skip imports, then extract segment if markers are specified
      let processed = skipImports(fullCode);
      processed = extractSegment(processed, resolvedFrom, resolvedTo);
      snippet = processed;
    } catch (e) {
      throw new Error(
        `ShowcaseSnippet: ${e.message} in file "${file}" for ${effectiveDatabase}/${language}`
      );
    }
  }

  const fileName = file.split('/').pop();

  return (
    <div className={styles.showcaseSnippet}>
      {showPicker && (
        <StackPicker
          databases={databases}
          showFullFile={showFullFile}
          onShowFullFileChange={setShowFullFile}
          fileName={fileName}
        />
      )}
      {title && <div className={styles.title}>{title}</div>}
      <CodeBlock language={PRISM_LANGUAGES[language]}>
        {snippet}
      </CodeBlock>
    </div>
  );
}

/**
 * ShowcaseSnippetGroup - Show multiple snippets that share a picker
 *
 * @param {Object} props
 * @param {React.ReactNode} props.children - ShowcaseSnippet components
 */
export function ShowcaseSnippetGroup({ children }) {
  return (
    <div className={styles.snippetGroup}>
      <StackPicker />
      <div className={styles.snippets}>
        {React.Children.map(children, child =>
          React.cloneElement(child, { showPicker: false })
        )}
      </div>
    </div>
  );
}

/**
 * Standalone picker for pages that want to control the selection
 * without showing a snippet
 */
export { StackPicker };
