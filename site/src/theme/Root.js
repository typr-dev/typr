/**
 * Root component wrapper for Docusaurus
 *
 * This wraps the entire application with providers like StackContext.
 */

import React from 'react';
import { StackProvider } from '../context/StackContext';

export default function Root({ children }) {
  return (
    <StackProvider>
      {children}
    </StackProvider>
  );
}
