import React from "react";
import styles from "./styles.module.css";
import IslandIsolated from "./IslandIsolated";
import IslandConnected from "./IslandConnected";

export default function BoundaryDiagram() {
  return (
    <div className={styles.container}>
      <div className={styles.headline}>
        Your system is an island of safety <span className={styles.headlinePunch}>in a sea of uncertainty.</span>
      </div>

      <div className={styles.cardRow}>
        <div className={styles.card}>
          <div className={styles.cardLabel}>Without Typr</div>
          <div className={styles.illustration}>
            <IslandIsolated />
          </div>
          <div className={styles.caption}>
            The compiler can't see beyond the shore.
          </div>
        </div>

        <div className={styles.card}>
          <div className={styles.cardLabelHighlight}>With Typr</div>
          <div className={styles.illustration}>
            <IslandConnected />
          </div>
          <div className={styles.captionConnected}>
            Bridges to the mainland. The compiler sees the whole landscape.
          </div>
        </div>
      </div>
    </div>
  );
}
