import React from 'react';

const features = [
  {
    number: '01',
    title: 'Type-Safe by Design',
    description: 'Tagless final with typed keys and values. Smart constructors validate config at compile time via Codec type class.',
  },
  {
    number: '02',
    title: 'Purely Functional',
    description: 'Resource-managed connections. Domain errors are values via ValkeyResponse ADT — pattern match, don\'t catch.',
  },
  {
    number: '03',
    title: 'Rust-Powered Core',
    description: 'Valkey Glide handles pooling, cluster topology, and client-side caching in its Rust engine. Scala gets the ergonomics.',
  },
  {
    number: '04',
    title: 'Full Command Coverage',
    description: 'Strings, Hashes, Lists, Sets, Sorted Sets, Streams, Geo, Bitmaps, HyperLogLog, Scripting, and more.',
  },
  {
    number: '05',
    title: 'Validated Configuration',
    description: 'ip4s types for host/port, Either-based smart constructors, opaque types for database IDs. Invalid states are unrepresentable.',
  },
  {
    number: '06',
    title: 'Client-Side Caching',
    description: 'Local TTL-based caching with configurable eviction (LRU/LFU) and observable hit/miss metrics.',
  },
];

export default function HomepageFeatures() {
  return (
    <section className="features-section">
      <div className="features-header">
        <h2>Built for production Scala</h2>
        <p>
          Everything you need to build reliable, high-throughput applications with Valkey.
        </p>
      </div>
      <div className="features-grid">
        {features.map((feature) => (
          <div key={feature.number} className="feature-card">
            <div className="feature-number">{feature.number}</div>
            <h3>{feature.title}</h3>
            <p>{feature.description}</p>
          </div>
        ))}
      </div>
    </section>
  );
}
