// @ts-check

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  docs: [
    {
      type: 'category',
      label: 'Getting Started',
      items: [
        'getting-started/quickstart',
        'getting-started/client',
        'getting-started/codecs',
        'getting-started/error-handling',
      ],
    },
    {
      type: 'category',
      label: 'Commands',
      items: [
        'commands/overview',
        'commands/strings',
        'commands/hashes',
        'commands/keys',
        'commands/lists',
        'commands/sets',
        'commands/sorted-sets',
        'commands/streams',
        'commands/geo',
        'commands/bitmaps',
        'commands/hyperloglog',
        'commands/connection',
        'commands/server',
        'commands/scripting',
      ],
    },
    {
      type: 'category',
      label: 'Guides',
      items: [
        'guides/client-side-caching',
      ],
    },
  ],
};

module.exports = sidebars;
