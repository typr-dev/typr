/**
 * StackContext - Global state for database and language selection
 *
 * This context provides:
 * - Selected database (postgres, mariadb, duckdb, oracle, sqlserver)
 * - Selected language (java, kotlin, scala)
 * - Persistence to localStorage
 * - Helper functions for checking availability
 */

import React, { createContext, useContext, useState, useEffect } from 'react';
import { DATABASES, LANGUAGES, DATABASE_LABELS, LANGUAGE_LABELS } from '../data/showcaseFiles';

const STORAGE_KEY = 'typr-stack-selection';

const defaultState = {
  database: 'postgres',
  language: 'java',
};

const StackContext = createContext(undefined);

export function StackProvider({ children }) {
  const [database, setDatabase] = useState(defaultState.database);
  const [language, setLanguage] = useState(defaultState.language);
  const [isInitialized, setIsInitialized] = useState(false);

  // Load from localStorage on mount
  useEffect(() => {
    if (typeof window !== 'undefined') {
      try {
        const stored = localStorage.getItem(STORAGE_KEY);
        if (stored) {
          const parsed = JSON.parse(stored);
          if (DATABASES.includes(parsed.database)) {
            setDatabase(parsed.database);
          }
          if (LANGUAGES.includes(parsed.language)) {
            setLanguage(parsed.language);
          }
        }
      } catch (e) {
        // Ignore localStorage errors
      }
      setIsInitialized(true);
    }
  }, []);

  // Save to localStorage on change
  useEffect(() => {
    if (isInitialized && typeof window !== 'undefined') {
      try {
        localStorage.setItem(STORAGE_KEY, JSON.stringify({ database, language }));
      } catch (e) {
        // Ignore localStorage errors
      }
    }
  }, [database, language, isInitialized]);

  const value = {
    database,
    language,
    setDatabase,
    setLanguage,
    databaseLabel: DATABASE_LABELS[database],
    languageLabel: LANGUAGE_LABELS[language],
    isInitialized,
  };

  return (
    <StackContext.Provider value={value}>
      {children}
    </StackContext.Provider>
  );
}

export function useStack() {
  const context = useContext(StackContext);
  if (context === undefined) {
    throw new Error('useStack must be used within a StackProvider');
  }
  return context;
}

// Re-export constants for convenience
export { DATABASES, LANGUAGES, DATABASE_LABELS, LANGUAGE_LABELS };
