// @ts-check

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
    tutorialSidebar: [
        {
            type: "category",
            label: "What is Typr?",
            collapsible: true,
            collapsed: false,
            items: [
                {type: "doc", id: "readme"},
                {type: "doc", id: "what-is/sql-is-king"},
                {type: "doc", id: "what-is/relations"},
                {type: "doc", id: "what-is/dsl"},
            ],
        },
        {type: "doc", id: "setup"},
        {type: "doc", id: "limitations"},
        {
            type: "category",
            label: "Type-safety",
            collapsible: true,
            collapsed: false,
            items: [
                {type: "doc", id: "type-safety/id-types"},
                {type: "doc", id: "type-safety/string-enums"},
                {type: "doc", id: "type-safety/open-string-enums"},
                {type: "doc", id: "type-safety/domains"},
                {type: "doc", id: "type-safety/arrays"},
                {type: "doc", id: "type-safety/date-time"},
                {type: "doc", id: "type-safety/defaulted-types"},
                {type: "doc", id: "type-safety/typo-types"},
                {type: "doc", id: "type-safety/type-flow"},
                {type: "doc", id: "type-safety/user-selected-types"},
            ],
        },
        {
            type: "category",
            label: "Testing",
            collapsible: true,
            collapsed: false,
            items: [
                {type: "doc", id: "other-features/testing-with-stubs"},
                {type: "doc", id: "other-features/testing-with-random-values"},
            ],
        },
        {
            type: "category",
            label: "Advanced Features",
            collapsible: true,
            collapsed: false,
            items: [
                {type: "doc", id: "other-features/dsl-in-depth"},
                {type: "doc", id: "other-features/streaming-inserts"},
                {type: "doc", id: "other-features/generate-into-multiple-projects"},
                {type: "doc", id: "other-features/json"},
                {type: "doc", id: "other-features/faster-compilation"},
                {type: "doc", id: "other-features/flexible"},
                {type: "doc", id: "other-features/clickable-links"},
                {type: "doc", id: "other-features/constraints"},
                {type: "doc", id: "other-features/scala-js-ready"},
            ],
        },
        {
            type: "category",
            label: "Patterns",
            collapsible: true,
            collapsed: false,
            items: [
                {type: "doc", id: "patterns/multi-repo"},
                {type: "doc", id: "patterns/dynamic-queries"},
            ],
        },
        {
            type: "category",
            label: "Customization",
            collapsible: true,
            collapsed: false,
            items: [
                {type: "doc", id: "customization/overview"},
                {type: "doc", id: "customization/customize-selected-relations"},
                {type: "doc", id: "customization/customize-sql-files"},
                {type: "doc", id: "customization/customize-naming"},
                {type: "doc", id: "customization/customize-nullability"},
                {type: "doc", id: "customization/customize-types"},
                {type: "doc", id: "customization/selector"},
            ],
        },
    ]
};

module.exports = sidebars;
