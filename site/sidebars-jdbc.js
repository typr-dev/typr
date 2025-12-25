// @ts-check

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
    jdbcSidebar: [
        {type: "doc", id: "readme", label: "Introduction"},
        {
            type: "category",
            label: "Database Types",
            items: [
                {type: "doc", id: "postgresql", label: "PostgreSQL"},
                {type: "doc", id: "mariadb", label: "MariaDB/MySQL"},
                {type: "doc", id: "duckdb", label: "DuckDB"},
                {type: "doc", id: "oracle", label: "Oracle"},
                {type: "doc", id: "sqlserver", label: "SQL Server"},
            ],
        },
    ]
};

module.exports = sidebars;
