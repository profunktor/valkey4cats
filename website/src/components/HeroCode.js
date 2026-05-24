import React from 'react';

export default function HeroCode() {
  return (
    <div className="hero-code">
      <div className="hero-code-header">
        <div className="hero-code-dots">
          <span></span>
          <span></span>
          <span></span>
        </div>
        <span className="hero-code-filename">App.scala</span>
      </div>
      <pre><code dangerouslySetInnerHTML={{ __html: codeHtml() }} /></pre>
    </div>
  );
}

function codeHtml() {
  return `<span class="kw">import</span> cats.effect.*
<span class="kw">import</span> dev.profunktor.valkey4cats.<span class="ty">Valkey</span>
<span class="kw">import</span> dev.profunktor.valkey4cats.model.ValkeyResponse.<span class="ty">Ok</span>

<span class="kw">object</span> <span class="ty">App</span> <span class="kw">extends</span> <span class="ty">IOApp.Simple</span>:

  <span class="kw">def</span> <span class="fn">run</span>: <span class="ty">IO</span>[<span class="ty">Unit</span>] =
    <span class="ty">Valkey</span>[<span class="ty">IO</span>].<span class="fn">utf8</span>(<span class="str">"valkey://localhost"</span>).<span class="fn">use</span> { cmd <span class="op">=&gt;</span>
      <span class="kw">for</span>
        _   <span class="op">&lt;-</span> cmd.<span class="fn">set</span>(<span class="str">"key"</span>, <span class="str">"hello, valkey"</span>)
        res <span class="op">&lt;-</span> cmd.<span class="fn">get</span>(<span class="str">"key"</span>)
        _   <span class="op">&lt;-</span> res <span class="kw">match</span>
          <span class="kw">case</span> <span class="ty">Ok</span>(<span class="ty">Some</span>(v)) <span class="op">=&gt;</span> <span class="ty">IO</span>.<span class="fn">println</span>(s<span class="str">"Got: $v"</span>)
          <span class="kw">case</span> _            <span class="op">=&gt;</span> <span class="ty">IO</span>.unit
      <span class="kw">yield</span> ()
    }`;
}
