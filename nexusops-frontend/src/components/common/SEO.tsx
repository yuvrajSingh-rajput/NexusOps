import React, { useEffect } from 'react';

export interface SEOProps {
  /** Page specific title. Will be formatted as "${title} | NexusOps" */
  title: string;
  /** Meta description for search engine previews */
  description?: string;
  /**
   * Whether to forbid search engines from indexing and following links.
   * Default is true (safe default for authenticated enterprise applications).
   * Set explicitly to false ONLY on public marketing / login / registration pages.
   */
  noIndex?: boolean;
  /** Canonical URL path (e.g. "/login" or full URL) */
  canonical?: string;
  /** Open Graph type (e.g. "website", "article") */
  ogType?: string;
  /** Open Graph preview image */
  ogImage?: string;
  /** Structured Data JSON-LD object for schema.org rich results */
  structuredData?: Record<string, any>;
}

function updateMetaTag(attrName: 'name' | 'property', attrValue: string, content: string | undefined) {
  if (content === undefined) return;
  let element = document.querySelector(`meta[${attrName}="${attrValue}"]`) as HTMLMetaElement | null;
  if (!element) {
    element = document.createElement('meta');
    element.setAttribute(attrName, attrValue);
    document.head.appendChild(element);
  }
  element.setAttribute('content', content);
}

function updateLinkTag(rel: string, href: string | undefined) {
  if (!href) return;
  let element = document.querySelector(`link[rel="${rel}"]`) as HTMLLinkElement | null;
  if (!element) {
    element = document.createElement('link');
    element.setAttribute('rel', rel);
    document.head.appendChild(element);
  }
  element.setAttribute('href', href);
}

/**
 * Reusable SEO hook that dynamically manages document title, meta robots tags,
 * canonical links, OpenGraph / Twitter cards, and JSON-LD structured data.
 */
export function useSEO({
  title,
  description = 'NexusOps – Intelligent Enterprise Operations Portal, Service Orchestration, and Workflow Approvals.',
  noIndex = true,
  canonical,
  ogType = 'website',
  ogImage = '/favicon.svg',
  structuredData,
}: SEOProps) {
  useEffect(() => {
    // 1. Update Document Title
    const formattedTitle = title ? `${title} | NexusOps` : 'NexusOps – Intelligent Enterprise Operations Portal';
    document.title = formattedTitle;

    // 2. Robots Directive (Keep authenticated pages strictly out of search engines!)
    const robotsDirective = noIndex ? 'noindex, nofollow, noarchive' : 'index, follow';
    updateMetaTag('name', 'robots', robotsDirective);
    updateMetaTag('name', 'googlebot', robotsDirective);

    // 3. Meta Description
    updateMetaTag('name', 'description', description);

    // 4. Canonical URL
    if (canonical) {
      const fullCanonical = canonical.startsWith('http')
        ? canonical
        : `${window.location.origin}${canonical}`;
      updateLinkTag('canonical', fullCanonical);
    }

    // 5. OpenGraph Tags
    updateMetaTag('property', 'og:title', formattedTitle);
    updateMetaTag('property', 'og:description', description);
    updateMetaTag('property', 'og:type', ogType);
    updateMetaTag('property', 'og:url', window.location.href);
    updateMetaTag('property', 'og:image', ogImage.startsWith('http') ? ogImage : `${window.location.origin}${ogImage}`);
    updateMetaTag('property', 'og:site_name', 'NexusOps');

    // 6. Twitter Card Tags
    updateMetaTag('name', 'twitter:card', 'summary');
    updateMetaTag('name', 'twitter:title', formattedTitle);
    updateMetaTag('name', 'twitter:description', description);
    updateMetaTag('name', 'twitter:image', ogImage.startsWith('http') ? ogImage : `${window.location.origin}${ogImage}`);

    // 7. Structured Data (JSON-LD)
    const scriptId = 'nexusops-schema-jsonld';
    let scriptTag = document.getElementById(scriptId) as HTMLScriptElement | null;

    if (structuredData) {
      if (!scriptTag) {
        scriptTag = document.createElement('script');
        scriptTag.id = scriptId;
        scriptTag.type = 'application/ld+json';
        document.head.appendChild(scriptTag);
      }
      scriptTag.textContent = JSON.stringify(structuredData);
    } else if (scriptTag) {
      scriptTag.remove();
    }

    return () => {
      // Cleanup structured data on page transition
      const existing = document.getElementById(scriptId);
      if (existing) {
        existing.remove();
      }
      // Re-enforce strict noindex on unmount for safety
      updateMetaTag('name', 'robots', 'noindex, nofollow, noarchive');
    };
  }, [title, description, noIndex, canonical, ogType, ogImage, structuredData]);
}

/**
 * Declarative component wrapper for `useSEO`.
 */
export const SEO: React.FC<SEOProps> = (props) => {
  useSEO(props);
  return null;
};
