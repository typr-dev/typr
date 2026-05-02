import React from "react";
import Link from "@docusaurus/Link";
import styles from "./styles.module.css";

export default function WhyTypo() {
    return (
        <section className={styles.whyTypo}>
            <div className="container">
                <h2 className={styles.title}>
                    Why Teams Choose Typr Over Everything Else
                </h2>
                
                <div className={styles.comparisonGrid}>
                    <div className={styles.comparisonItem}>
                        <h3>vs. Traditional ORMs</h3>
                        <ul className={styles.benefitsList}>
                            <li>✅ <strong>Zero complexity debt</strong> - No entity managers, session state, or lazy loading issues</li>
                            <li>✅ <strong>Predictable performance</strong> - No N+1 queries or hidden roundtrips</li>
                            <li>✅ <strong>SQL-first</strong> - Use the full power of PostgreSQL, not a subset</li>
                            <li>✅ <strong>Debuggable</strong> - See exactly what SQL runs, no abstraction layers</li>
                        </ul>
                    </div>
                    
                    <div className={styles.comparisonItem}>
                        <h3>vs. Writing SQL by Hand</h3>
                        <ul className={styles.benefitsList}>
                            <li>✅ <strong>Zero boilerplate</strong> - No manual mapping code ever</li>
                            <li>✅ <strong>Type safety</strong> - Catch errors at compile time, not runtime</li>
                            <li>✅ <strong>Faster compilation</strong> - No runtime reflection or macro magic</li>
                            <li>✅ <strong>Automatic updates</strong> - Schema changes = instant code updates</li>
                        </ul>
                    </div>
                    
                    <div className={styles.comparisonItem}>
                        <h3>vs. JOOQ</h3>
                        <ul className={styles.benefitsList}>
                            <li>✅ <strong>Stronger type safety</strong> - Specific ID types and proper nullability with Option[T]</li>
                            <li>✅ <strong>Open source</strong> - No commercial licensing headaches</li>
                            <li>✅ <strong>Scala-native</strong> - Idiomatic code, not Java translations</li>
                            <li>✅ <strong>PostgreSQL-focused</strong> - Deep integration, not generic</li>
                        </ul>
                    </div>
                </div>
                
                <div className={styles.bottomCTA}>
                    <h3>Ready to Ship Faster with Fewer Bugs?</h3>
                    <p>Join developers who've discovered the joy of type-safe database development.</p>
                    <div className={styles.ctaButtons}>
                        <Link className="button button--primary button--lg" to="/typr/boundaries/databases/setup">
                            Get Started Now
                        </Link>
                        <Link className="button button--outline button--lg" to="/typr/boundaries/databases/">
                            Read the Docs
                        </Link>
                    </div>
                </div>
            </div>
        </section>
    );
}