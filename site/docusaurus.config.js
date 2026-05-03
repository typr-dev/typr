// @ts-check
// Note: type annotations allow type checking and IDEs autocompletion
const path = require('path');

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: "Typr",
  tagline: "End-to-end type safety for Java, Kotlin, and Scala",
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
          id: 'typr',
          path: 'docs-typr',
          routeBasePath: 'typr',
          sidebarPath: require.resolve("./sidebars-typr.js"),
        },
        theme: {
          customCss: require.resolve("./src/css/custom.css"),
        },
      }),
    ],
  ],

  plugins: [
    function rawLoaderPlugin() {
      return {
        name: 'raw-loader-plugin',
        configureWebpack() {
          return {
            module: {
              rules: [
                {
                  test: /\.(java|kt|scala)$/,
                  type: 'asset/source',
                },
              ],
            },
          };
        },
      };
    },
  ],

  clientModules: [
    require.resolve('./tracker.js'),
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      announcementBar: {
        id: 'under_development',
        content: '🚧 <strong>Under Development</strong> — This site and product are launching early 2026.',
        backgroundColor: '#7c3aed',
        textColor: '#ffffff',
        isCloseable: false,
      },
      navbar: {
        title: "Typr",
        logo: {
          alt: "Typr",
          src: "img/logo.svg",
        },
        items: [
          {
            to: '/typr/',
            label: 'Docs',
            position: 'left',
            activeBaseRegex: '/typr/',
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
            title: "Product",
            items: [
              {
                label: "Documentation",
                to: "/typr/",
              },
            ],
          },
          {
            title: "Boundaries",
            items: [
              {
                label: "Databases",
                to: "/typr/boundaries/databases/",
              },
              {
                label: "REST APIs",
                to: "/typr/boundaries/apis/",
              },
              {
                label: "Events (Avro/Kafka)",
                to: "/typr/boundaries/events/",
              },
              {
                label: "Unified Types",
                to: "/typr/unified-types/",
              },
            ],
          },
          {
            title: "Links",
            items: [
              {
                label: "Foundations JDBC",
                href: "https://foundations.typr.dev",
              },
              {
                label: "GitHub",
                href: "https://github.com/oyvindberg/typr",
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
