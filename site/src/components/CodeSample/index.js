/**
 * CodeSample - Displays code from showcase-generated based on current stack selection
 *
 * This component:
 * - Reads the current database/language from StackContext
 * - Looks up the requested file in the code samples
 * - Renders the code with syntax highlighting
 * - Shows a helpful message if the sample doesn't exist for current selection
 * - Can show tabs for available alternatives
 */

import React from 'react';
import CodeBlock from '@theme/CodeBlock';
import { useStack } from '../../context/StackContext';
import { getCodeSample, getAllSamplesForFile, DATABASES, LANGUAGES, DATABASE_LABELS, LANGUAGE_LABELS } from '../../data/codeSamples';
import styles from './styles.module.css';

// Map language to Prism language for syntax highlighting
const PRISM_LANGUAGES = {
  java: 'java',
  kotlin: 'kotlin',
  scala: 'scala',
};

/**
 * Main CodeSample component
 *
 * @param {Object} props
 * @param {string} props.file - The file name to display (e.g., "EmployeeRow")
 * @param {string} [props.title] - Optional title to show above the code
 * @param {boolean} [props.showTabs] - Whether to show language/db tabs when current selection doesn't exist
 * @param {string} [props.className] - Additional CSS class
 */
export default function CodeSample({ file, title, showTabs = false, className }) {
  const { database, language, setDatabase, setLanguage, databaseLabel, languageLabel } = useStack();

  const code = getCodeSample(database, language, file);
  const allSamples = getAllSamplesForFile(file);

  // If we have the code for current selection, show it
  if (code) {
    return (
      <div className={`${styles.codeSample} ${className || ''}`}>
        {title && <div className={styles.title}>{title}</div>}
        <CodeBlock language={PRISM_LANGUAGES[language]} title={`${file} (${databaseLabel} / ${languageLabel})`}>
          {code}
        </CodeBlock>
      </div>
    );
  }

  // Find what's available
  const availableCombinations = [];
  for (const db of DATABASES) {
    for (const lang of LANGUAGES) {
      if (allSamples[db]?.[lang]) {
        availableCombinations.push({ db, lang });
      }
    }
  }

  // If nothing is available at all, show error
  if (availableCombinations.length === 0) {
    return (
      <div className={`${styles.codeSample} ${styles.notFound} ${className || ''}`}>
        <div className={styles.notFoundMessage}>
          Code sample <code>{file}</code> not found
        </div>
      </div>
    );
  }

  // If showTabs, offer to switch to an available combination
  if (showTabs) {
    return (
      <div className={`${styles.codeSample} ${className || ''}`}>
        {title && <div className={styles.title}>{title}</div>}
        <div className={styles.notAvailable}>
          <p>
            <code>{file}</code> is not available for {databaseLabel}/{languageLabel}.
          </p>
          <p>Available for:</p>
          <div className={styles.alternativeButtons}>
            {availableCombinations.slice(0, 6).map(({ db, lang }) => (
              <button
                key={`${db}-${lang}`}
                className={styles.alternativeButton}
                onClick={() => {
                  setDatabase(db);
                  setLanguage(lang);
                }}
              >
                {DATABASE_LABELS[db]} / {LANGUAGE_LABELS[lang]}
              </button>
            ))}
          </div>
        </div>
      </div>
    );
  }

  // Default: show first available with a note
  const firstAvailable = availableCombinations[0];
  const fallbackCode = allSamples[firstAvailable.db][firstAvailable.lang];

  return (
    <div className={`${styles.codeSample} ${className || ''}`}>
      {title && <div className={styles.title}>{title}</div>}
      <div className={styles.fallbackNote}>
        Not available for {databaseLabel}/{languageLabel}. Showing {DATABASE_LABELS[firstAvailable.db]}/{LANGUAGE_LABELS[firstAvailable.lang]}:
      </div>
      <CodeBlock
        language={PRISM_LANGUAGES[firstAvailable.lang]}
        title={`${file} (${DATABASE_LABELS[firstAvailable.db]} / ${LANGUAGE_LABELS[firstAvailable.lang]})`}
      >
        {fallbackCode}
      </CodeBlock>
    </div>
  );
}

/**
 * Multi-language code sample that shows tabs for all available languages
 * for the current database selection.
 */
export function CodeSampleTabs({ file, title, className }) {
  const { database, language, setLanguage, databaseLabel } = useStack();
  const allSamples = getAllSamplesForFile(file);

  // Get languages available for current database
  const availableLanguages = LANGUAGES.filter((lang) => allSamples[database]?.[lang]);

  if (availableLanguages.length === 0) {
    return (
      <div className={`${styles.codeSample} ${styles.notFound} ${className || ''}`}>
        <div className={styles.notFoundMessage}>
          Code sample <code>{file}</code> not available for {databaseLabel}
        </div>
      </div>
    );
  }

  // Use selected language if available, otherwise first available
  const activeLanguage = availableLanguages.includes(language) ? language : availableLanguages[0];
  const code = allSamples[database][activeLanguage];

  return (
    <div className={`${styles.codeSample} ${styles.withTabs} ${className || ''}`}>
      {title && <div className={styles.title}>{title}</div>}
      <div className={styles.languageTabs}>
        {availableLanguages.map((lang) => (
          <button
            key={lang}
            className={`${styles.languageTab} ${activeLanguage === lang ? styles.activeTab : ''}`}
            onClick={() => setLanguage(lang)}
          >
            {LANGUAGE_LABELS[lang]}
          </button>
        ))}
      </div>
      <CodeBlock language={PRISM_LANGUAGES[activeLanguage]}>
        {code}
      </CodeBlock>
    </div>
  );
}

/**
 * Shows a side-by-side comparison of the same file across multiple languages
 */
export function CodeSampleComparison({ file, title, languages = LANGUAGES, className }) {
  const { database, databaseLabel } = useStack();
  const allSamples = getAllSamplesForFile(file);

  const availableLangs = languages.filter((lang) => allSamples[database]?.[lang]);

  if (availableLangs.length === 0) {
    return (
      <div className={`${styles.codeSample} ${styles.notFound} ${className || ''}`}>
        <div className={styles.notFoundMessage}>
          Code sample <code>{file}</code> not available for {databaseLabel}
        </div>
      </div>
    );
  }

  return (
    <div className={`${styles.codeComparison} ${className || ''}`}>
      {title && <div className={styles.title}>{title}</div>}
      <div className={styles.comparisonGrid} style={{ gridTemplateColumns: `repeat(${availableLangs.length}, 1fr)` }}>
        {availableLangs.map((lang) => (
          <div key={lang} className={styles.comparisonColumn}>
            <div className={styles.comparisonHeader}>{LANGUAGE_LABELS[lang]}</div>
            <CodeBlock language={PRISM_LANGUAGES[lang]}>
              {allSamples[database][lang]}
            </CodeBlock>
          </div>
        ))}
      </div>
    </div>
  );
}
