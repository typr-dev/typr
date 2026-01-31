/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  avroSidebar: [
    'readme',
    'setup',
    {
      type: 'category',
      label: 'Concepts',
      items: [
        'what-is/schemas',
        'what-is/wire-formats',
        'what-is/effect-types',
      ],
    },
    {
      type: 'category',
      label: 'Kafka',
      items: [
        'kafka/producers',
        'kafka/consumers',
        'kafka/headers',
        'kafka/multi-event',
      ],
    },
    {
      type: 'category',
      label: 'Type Safety',
      items: [
        'type-safety/wrapper-types',
        'type-safety/precise-types',
        'type-safety/unions',
      ],
    },
    {
      type: 'category',
      label: 'Kafka RPC',
      items: [
        'rpc/protocols',
        'rpc/result-adt',
        'rpc/spring',
        'rpc/quarkus',
      ],
    },
    {
      type: 'category',
      label: 'Reference',
      items: [
        'reference/options',
        'reference/type-mappings',
        'reference/limitations',
      ],
    },
  ],
};

module.exports = sidebars;
