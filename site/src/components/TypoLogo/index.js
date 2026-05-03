import React from "react";
import styles from "./styles.module.css";

export default function TypoLogo({ size = 100, animated = true }) {
    return (
        <div className={styles.logoContainer} style={{ width: size, height: size }}>
            <svg
                viewBox="0 0 200 200"
                xmlns="http://www.w3.org/2000/svg"
                className={styles.staticLogo}
            >
                <defs>
                    <linearGradient id="simpleGrad" x1="0%" y1="0%" x2="100%" y2="100%">
                        <stop offset="0%" stopColor="#a78bfa" />
                        <stop offset="100%" stopColor="#7c3aed" />
                    </linearGradient>
                </defs>

                <g transform="translate(100, 100)">
                    {/* Simple abstract shape - made bigger */}
                    <path
                        d="M -50 -60 Q -70 0 -50 60 Q 0 30 50 60 Q 70 0 50 -60 Q 0 -30 -50 -60"
                        fill="url(#simpleGrad)"
                    />
                </g>
            </svg>
        </div>
    );
}
