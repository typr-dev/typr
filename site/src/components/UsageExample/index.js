/**
 * UsageExample - Displays usage example code from usage-examples directory
 *
 * Similar to ShowcaseSnippet but reads from the checked-in usage-examples directory
 */

import React from 'react';
import CodeBlock from '@theme/CodeBlock';
import { useStack, DATABASES, LANGUAGES, DATABASE_LABELS, LANGUAGE_LABELS } from '../../context/StackContext';
import { getExampleFile } from '../../data/showcaseFiles';
import styles from '../ShowcaseSnippet/styles.module.css';

// Map language to Prism language for syntax highlighting
const PRISM_LANGUAGES = {
  java: 'java',
  kotlin: 'kotlin',
  scala: 'scala',
};

/**
 * Language picker bar
 */
function LanguagePicker({ databases, fileName }) {
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
    </div>
  );
}

/**
 * UsageExample - Main component
 *
 * @param {Object} props
 * @param {string} props.file - File path without extension (e.g., "TestInsertExample")
 * @param {string} [props.title] - Optional title
 * @param {boolean} [props.showPicker=true] - Whether to show the language picker
 * @param {string[]} [props.databases] - Optional list of databases to show
 */
export default function UsageExample({ file, title, showPicker = true, databases }) {
  const { database, language } = useStack();

  // If databases are restricted and current selection isn't allowed, use first allowed database
  const effectiveDatabase = databases && !databases.includes(database)
    ? databases[0]
    : database;

  const code = getExampleFile(effectiveDatabase, language, file);

  // FAIL HARD if file not found
  if (!code) {
    throw new Error(
      `UsageExample: File "${file}" not found for ${effectiveDatabase}/${language}. ` +
      `Check that the file exists in usage-examples/${effectiveDatabase}/${language}/${file}.*`
    );
  }

  const fileName = file.split('/').pop();

  return (
    <div className={styles.showcaseSnippet}>
      {showPicker && (
        <LanguagePicker
          databases={databases}
          fileName={fileName}
        />
      )}
      {title && <div className={styles.title}>{title}</div>}
      <CodeBlock language={PRISM_LANGUAGES[language]}>
        {code}
      </CodeBlock>
    </div>
  );
}
