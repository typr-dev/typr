import React from "react";

export default function IslandConnected() {
  return (
    <svg
      viewBox="0 0 400 200"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      style={{ width: "100%", maxWidth: "450px", height: "auto" }}
    >
      {/* Solid ground gradient - everything is connected */}
      <defs>
        <linearGradient id="groundGradient" x1="0%" y1="0%" x2="0%" y2="100%">
          <stop offset="0%" stopColor="#f5f3ff" />
          <stop offset="100%" stopColor="#ede9fe" />
        </linearGradient>
        <linearGradient id="bridgeGradient" x1="0%" y1="0%" x2="100%" y2="0%">
          <stop offset="0%" stopColor="#8b5cf6" stopOpacity="0.2" />
          <stop offset="50%" stopColor="#8b5cf6" stopOpacity="0.4" />
          <stop offset="100%" stopColor="#8b5cf6" stopOpacity="0.2" />
        </linearGradient>
        <filter id="glowConnected">
          <feGaussianBlur stdDeviation="2" result="coloredBlur"/>
          <feMerge>
            <feMergeNode in="coloredBlur"/>
            <feMergeNode in="SourceGraphic"/>
          </feMerge>
        </filter>
      </defs>

      {/* Base ground - continuous foundation */}
      <rect x="0" y="160" width="400" height="40" fill="#ede9fe" rx="4" />

      {/* API - now solid ground */}
      <g filter="url(#glowConnected)">
        <rect x="0" y="50" width="100" height="100" rx="8" fill="url(#groundGradient)" stroke="#8b5cf6" strokeWidth="2" />
      </g>
      <text x="50" y="85" textAnchor="middle" fontSize="11" fontWeight="600" fill="#8b5cf6" fontFamily="system-ui">API</text>
      <text x="50" y="110" textAnchor="middle" fontSize="10" fill="#7c3aed" fontFamily="system-ui" fontWeight="500">typed</text>

      {/* Bridge from API to System */}
      <rect x="100" y="70" width="50" height="60" fill="url(#bridgeGradient)" />
      <line x1="100" y1="70" x2="150" y2="70" stroke="#8b5cf6" strokeWidth="2" />
      <line x1="100" y1="130" x2="150" y2="130" stroke="#8b5cf6" strokeWidth="2" />
      {/* Bridge pillars */}
      <rect x="115" y="130" width="6" height="30" fill="#c4b5fd" rx="1" />
      <rect x="129" y="130" width="6" height="30" fill="#c4b5fd" rx="1" />

      {/* The System - Central and connected */}
      <g filter="url(#glowConnected)">
        <rect x="150" y="40" width="100" height="120" rx="12" fill="url(#groundGradient)" stroke="#8b5cf6" strokeWidth="2.5" />
      </g>
      <text x="200" y="70" textAnchor="middle" fontSize="10" fontWeight="700" fill="#8b5cf6" fontFamily="system-ui" letterSpacing="0.5">YOUR SYSTEM</text>
      <text x="200" y="95" textAnchor="middle" fontSize="11" fill="#6b7280" fontFamily="system-ui">type-checked</text>
      <text x="200" y="112" textAnchor="middle" fontSize="11" fill="#6b7280" fontFamily="system-ui">linted</text>
      <text x="200" y="129" textAnchor="middle" fontSize="11" fill="#6b7280" fontFamily="system-ui">tested</text>


      {/* Bridge from System to Database */}
      <rect x="250" y="70" width="50" height="60" fill="url(#bridgeGradient)" />
      <line x1="250" y1="70" x2="300" y2="70" stroke="#8b5cf6" strokeWidth="2" />
      <line x1="250" y1="130" x2="300" y2="130" stroke="#8b5cf6" strokeWidth="2" />
      {/* Bridge pillars */}
      <rect x="265" y="130" width="6" height="30" fill="#c4b5fd" rx="1" />
      <rect x="279" y="130" width="6" height="30" fill="#c4b5fd" rx="1" />

      {/* Database - now solid ground */}
      <g filter="url(#glowConnected)">
        <rect x="300" y="50" width="100" height="100" rx="8" fill="url(#groundGradient)" stroke="#8b5cf6" strokeWidth="2" />
      </g>
      <text x="350" y="85" textAnchor="middle" fontSize="11" fontWeight="600" fill="#8b5cf6" fontFamily="system-ui">DATABASE</text>
      <text x="350" y="110" textAnchor="middle" fontSize="10" fill="#7c3aed" fontFamily="system-ui" fontWeight="500">typed</text>

      {/* Foundation pillars under main structures */}
      <rect x="40" y="150" width="20" height="10" fill="#c4b5fd" rx="2" />
      <rect x="190" y="160" width="20" height="10" fill="#c4b5fd" rx="2" />
      <rect x="340" y="150" width="20" height="10" fill="#c4b5fd" rx="2" />
    </svg>
  );
}
