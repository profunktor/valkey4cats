// @ts-check

const { themes } = require('prism-react-renderer');
const lightCodeTheme = themes.github;
const darkCodeTheme = themes.dracula;

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'Valkey4Cats',
  tagline: 'Purely functional Valkey client for Scala',
  url: 'https://valkey.profunktor.dev',
  baseUrl: '/',
  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'warn',
  favicon: 'img/favicon.svg',
  organizationName: 'profunktor',
  projectName: 'valkey4cats',
  plugins: [require.resolve('docusaurus-lunr-search')],

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          sidebarPath: require.resolve('./sidebars.js'),
          editUrl: 'https://github.com/profunktor/valkey4cats/edit/main/website/',
        },
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
        blog: false,
      }),
    ],
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      navbar: {
        title: 'Valkey4Cats',
        logo: {
          alt: 'Valkey4Cats Logo',
          src: 'img/logo.svg',
        },
        items: [
          {
            type: 'doc',
            docId: 'getting-started/quickstart',
            position: 'left',
            label: 'Documentation',
          },
          {
            href: 'https://github.com/profunktor/valkey4cats',
            label: 'GitHub',
            position: 'right',
          },
        ],
      },
      footer: {
        style: 'dark',
        links: [
          {
            title: 'Docs',
            items: [
              {
                label: 'Quick Start',
                to: '/docs/getting-started/quickstart',
              },
              {
                label: 'Client Configuration',
                to: '/docs/getting-started/client',
              },
            ],
          },
          {
            title: 'Community',
            items: [
              {
                label: 'GitHub',
                href: 'https://github.com/profunktor/valkey4cats',
              },
              {
                label: 'Typelevel Discord',
                href: 'https://discord.gg/typelevel',
              },
            ],
          },
          {
            title: 'More',
            items: [
              {
                label: 'Valkey',
                href: 'https://valkey.io',
              },
              {
                label: 'Valkey Glide',
                href: 'https://github.com/valkey-io/valkey-glide',
              },
            ],
          },
        ],
        copyright: `Copyright © ${new Date().getFullYear()} Valkey4Cats Contributors. Built with Docusaurus.`,
      },
      prism: {
        theme: lightCodeTheme,
        darkTheme: darkCodeTheme,
        additionalLanguages: ['java', 'scala'],
      },
    }),
};

module.exports = config;
