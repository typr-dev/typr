// @ts-check
// Note: type annotations allow type checking and IDEs autocompletion

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: "Typo",
  tagline: "Type-safe code generation for Postgres and OpenAPI",
  url: "https://typo.oyvindberg.dev",
  baseUrl: "/",
  onBrokenLinks: "throw",
  onBrokenMarkdownLinks: "warn",
  favicon: "img/favicon.ico",

  // Modern browsers prefer SVG favicons
  headTags: [
    {
      tagName: 'link',
      attributes: {
        rel: 'icon',
        type: 'image/svg+xml',
        href: '/img/favicon.svg',
      },
    },
  ],

  // GitHub pages deployment config.
  organizationName: "oyvindberg",
  projectName: "typr",
  trailingSlash: true,

  i18n: {
    defaultLocale: "en",
    locales: ["en"],
  },

  presets: [
    [
      "classic",
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          id: 'db',
          path: 'docs-db',
          routeBasePath: 'db',
          sidebarPath: require.resolve("./sidebars-db.js"),
        },
        theme: {
          customCss: require.resolve("./src/css/custom.css"),
        },
      }),
    ],
  ],

  plugins: [
    [
      '@docusaurus/plugin-content-docs',
      {
        id: 'api',
        path: 'docs-api',
        routeBasePath: 'api',
        sidebarPath: require.resolve('./sidebars-api.js'),
      },
    ],
    [
      '@docusaurus/plugin-content-docs',
      {
        id: 'jdbc',
        path: 'docs-jdbc',
        routeBasePath: 'jdbc',
        sidebarPath: require.resolve('./sidebars-jdbc.js'),
      },
    ],
  ],

  clientModules: [
    require.resolve('./tracker.js'),
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      navbar: {
        title: "Typo",
        logo: {
          alt: "Typo",
          src: "img/logo.svg",
        },
        items: [
          {
            to: '/db/',
            label: 'Typo DB',
            position: 'left',
            activeBaseRegex: '/db/',
          },
          {
            to: '/api/',
            label: 'Typo API',
            position: 'left',
            activeBaseRegex: '/api/',
          },
          {
            to: '/jdbc/',
            label: 'Foundations JDBC',
            position: 'left',
            activeBaseRegex: '/jdbc/',
          },
          {to: 'blog', label: 'Blog', position: 'left'},
          {
            href: "https://github.com/oyvindberg/typr",
            label: "GitHub",
            position: "right",
          },
        ],
      },
      footer: {
        style: "dark",
        links: [
          {
            title: "Products",
            items: [
              {
                label: "Typo DB",
                to: "/db/",
              },
              {
                label: "Typo API",
                to: "/api/",
              },
              {
                label: "Foundations JDBC",
                to: "/jdbc/",
              },
            ],
          },
          {
            title: "Links",
            items: [
              {
                label: "GitHub",
                to: "https://github.com/oyvindberg/typr",
              },
            ],
          },
        ],
        copyright: `Copyright © ${new Date().getFullYear()} Øyvind Raddum Berg`,
      },
      prism: {
        theme: require("prism-react-renderer").themes.github,
        darkTheme: require("prism-react-renderer").themes.dracula,
        additionalLanguages: ["java", "scala", "kotlin", "yaml", "sql"],
      },
    }),
};

module.exports = config;
