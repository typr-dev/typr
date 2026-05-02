import React from "react";

export default function IslandIsolated() {
  return (
    <svg
      viewBox="0 0 400 200"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      style={{ width: "100%", maxWidth: "450px", height: "auto" }}
    >
      {/* Foggy/uncertain sea background */}
      <defs>
        <linearGradient id="seaGradient" x1="0%" y1="0%" x2="0%" y2="100%">
          <stop offset="0%" stopColor="#e5e5e5" stopOpacity="0.3" />
          <stop offset="100%" stopColor="#d4d4d4" stopOpacity="0.5" />
        </linearGradient>
        <linearGradient id="islandGradient" x1="0%" y1="0%" x2="0%" y2="100%">
          <stop offset="0%" stopColor="#f5f3ff" />
          <stop offset="100%" stopColor="#ede9fe" />
        </linearGradient>
        <filter id="glow">
          <feGaussianBlur stdDeviation="2" result="coloredBlur"/>
          <feMerge>
            <feMergeNode in="coloredBlur"/>
            <feMergeNode in="SourceGraphic"/>
          </feMerge>
        </filter>
      </defs>

      {/* Sea/uncertainty on left */}
      <rect x="0" y="50" width="120" height="100" rx="8" fill="url(#seaGradient)" stroke="#d4d4d4" strokeWidth="2" strokeDasharray="6 4" />
      <text x="60" y="85" textAnchor="middle" fontSize="11" fontWeight="600" fill="#737373" fontFamily="system-ui">API</text>
      <text x="60" y="115" textAnchor="middle" fontSize="28" fill="#a3a3a3" fontFamily="system-ui">?</text>

      {/* Wavy lines suggesting turbulent sea - left gap */}
      <path d="M 122 95 Q 128 90, 135 95 Q 142 100, 148 95" stroke="#d4d4d4" strokeWidth="1.5" fill="none" opacity="0.6" />
      <path d="M 122 110 Q 128 105, 135 110 Q 142 115, 148 110" stroke="#d4d4d4" strokeWidth="1.5" fill="none" opacity="0.4" />

      {/* The Island - Your System */}
      <g filter="url(#glow)">
        <rect x="150" y="40" width="100" height="120" rx="12" fill="url(#islandGradient)" stroke="#8b5cf6" strokeWidth="2.5" />
      </g>
      <text x="200" y="70" textAnchor="middle" fontSize="10" fontWeight="700" fill="#8b5cf6" fontFamily="system-ui" letterSpacing="0.5">YOUR SYSTEM</text>
      <text x="200" y="95" textAnchor="middle" fontSize="11" fill="#6b7280" fontFamily="system-ui">type-checked</text>
      <text x="200" y="112" textAnchor="middle" fontSize="11" fill="#6b7280" fontFamily="system-ui">linted</text>
      <text x="200" y="129" textAnchor="middle" fontSize="11" fill="#6b7280" fontFamily="system-ui">tested</text>


      {/* Wavy lines suggesting turbulent sea - right gap */}
      <path d="M 252 95 Q 258 90, 265 95 Q 272 100, 278 95" stroke="#d4d4d4" strokeWidth="1.5" fill="none" opacity="0.6" />
      <path d="M 252 110 Q 258 105, 265 110 Q 272 115, 278 110" stroke="#d4d4d4" strokeWidth="1.5" fill="none" opacity="0.4" />

      {/* Sea/uncertainty on right */}
      <rect x="280" y="50" width="120" height="100" rx="8" fill="url(#seaGradient)" stroke="#d4d4d4" strokeWidth="2" strokeDasharray="6 4" />
      <text x="340" y="85" textAnchor="middle" fontSize="11" fontWeight="600" fill="#737373" fontFamily="system-ui">DATABASE</text>
      <text x="340" y="115" textAnchor="middle" fontSize="28" fill="#a3a3a3" fontFamily="system-ui">?</text>

      {/* Fog wisps */}
      <ellipse cx="80" cy="170" rx="60" ry="15" fill="#e5e5e5" opacity="0.4" />
      <ellipse cx="320" cy="170" rx="60" ry="15" fill="#e5e5e5" opacity="0.4" />
    </svg>
  );
}
