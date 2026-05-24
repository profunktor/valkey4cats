import React from 'react';
import Layout from '@theme/Layout';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import HomepageFeatures from '../components/HomepageFeatures';
import HeroCode from '../components/HeroCode';

function HomepageHero() {
  return (
    <section className="hero-section">
      <div className="hero-content">
        <div className="hero-text">
          <div className="hero-eyebrow">Scala 3 + Cats Effect</div>
          <h1 className="hero-title">
            A <em>purely functional</em> Valkey client
          </h1>
          <p className="hero-subtitle">
            Type-safe commands, Resource-managed connections, and a Rust-powered core.
            Domain errors are values, not exceptions.
          </p>
          <div className="hero-buttons">
            <Link className="hero-btn-primary" to="/docs/getting-started/quickstart">
              Get Started
              <span aria-hidden="true">&rarr;</span>
            </Link>
            <Link className="hero-btn-secondary" href="https://github.com/profunktor/valkey4cats">
              GitHub
            </Link>
          </div>
        </div>
        <HeroCode />
      </div>
    </section>
  );
}

export default function Home() {
  const { siteConfig } = useDocusaurusContext();
  return (
    <Layout
      title={siteConfig.title}
      description="Purely functional Valkey client for Scala, built on Cats Effect and Valkey Glide">
      <HomepageHero />
      <HomepageFeatures />
    </Layout>
  );
}
