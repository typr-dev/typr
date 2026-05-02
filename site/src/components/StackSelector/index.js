/**
 * StackSelector - Dropdown component for selecting database and language
 *
 * Uses two dropdown selects for database and language.
 * Selection is stored in StackContext and persisted to localStorage.
 */

import React from 'react';
import { useStack, DATABASES, LANGUAGES, DATABASE_LABELS, LANGUAGE_LABELS } from '../../context/StackContext';
import styles from './styles.module.css';

export default function StackSelector({ className }) {
  const { database, language, setDatabase, setLanguage, isInitialized } = useStack();

  if (!isInitialized) {
    return (
      <div className={`${styles.stackSelector} ${className || ''}`}>
        <div className={styles.selectorGroup}>
          <label className={styles.label}>Database:</label>
          <select className={styles.select} disabled>
            <option>Loading...</option>
          </select>
        </div>
        <div className={styles.selectorGroup}>
          <label className={styles.label}>Language:</label>
          <select className={styles.select} disabled>
            <option>Loading...</option>
          </select>
        </div>
      </div>
    );
  }

  return (
    <div className={`${styles.stackSelector} ${className || ''}`}>
      <div className={styles.selectorGroup}>
        <label className={styles.label} htmlFor="db-select">Database:</label>
        <select
          id="db-select"
          className={styles.select}
          value={database}
          onChange={(e) => setDatabase(e.target.value)}
        >
          {DATABASES.map((db) => (
            <option key={db} value={db}>
              {DATABASE_LABELS[db]}
            </option>
          ))}
        </select>
      </div>

      <div className={styles.selectorGroup}>
        <label className={styles.label} htmlFor="lang-select">Language:</label>
        <select
          id="lang-select"
          className={styles.select}
          value={language}
          onChange={(e) => setLanguage(e.target.value)}
        >
          {LANGUAGES.map((lang) => (
            <option key={lang} value={lang}>
              {LANGUAGE_LABELS[lang]}
            </option>
          ))}
        </select>
      </div>
    </div>
  );
}

/**
 * Compact inline version showing just the current selection as text
 */
export function StackSelectorInline() {
  const { databaseLabel, languageLabel } = useStack();
  return (
    <span className={styles.inlineSelector}>
      {databaseLabel} / {languageLabel}
    </span>
  );
}
