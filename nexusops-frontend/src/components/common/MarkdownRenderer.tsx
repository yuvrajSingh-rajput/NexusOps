import React from 'react';

interface MarkdownRendererProps {
  content: string;
  className?: string;
}

export const MarkdownRenderer: React.FC<MarkdownRendererProps> = ({ content, className = '' }) => {
  if (!content) return null;

  const blocks = parseBlocks(content);

  return (
    <div className={`space-y-3 text-slate-800 leading-relaxed ${className}`}>
      {blocks.map((block, index) => renderBlock(block, index))}
    </div>
  );
};

// ============================================================================
// Block Parsing
// ============================================================================

type Block =
  | { type: 'h1'; text: string }
  | { type: 'h2'; text: string }
  | { type: 'h3'; text: string }
  | { type: 'h4'; text: string }
  | { type: 'blockquote'; text: string }
  | { type: 'code'; lang: string; code: string }
  | { type: 'ul'; items: string[] }
  | { type: 'ol'; items: string[] }
  | { type: 'table'; headers: string[]; rows: string[][] }
  | { type: 'hr' }
  | { type: 'meta'; items: { label: string; value: string }[] }
  | { type: 'citation'; text: string }
  | { type: 'p'; text: string };

function parseBlocks(markdown: string): Block[] {
  const lines = markdown.replace(/\r\n/g, '\n').split('\n');
  const blocks: Block[] = [];
  let i = 0;

  while (i < lines.length) {
    const line = lines[i];
    const trimmed = line.trim();

    // Empty lines
    if (!trimmed) {
      i++;
      continue;
    }

    // Code blocks
    if (trimmed.startsWith('```')) {
      const lang = trimmed.slice(3).trim();
      const codeLines: string[] = [];
      i++;
      while (i < lines.length && !lines[i].trim().startsWith('```')) {
        codeLines.push(lines[i]);
        i++;
      }
      if (i < lines.length) i++; // consume closing ```
      blocks.push({ type: 'code', lang, code: codeLines.join('\n') });
      continue;
    }

    // Horizontal Rule
    if (/^(\-{3,}|\*{3,}|_{3,})$/.test(trimmed)) {
      blocks.push({ type: 'hr' });
      i++;
      continue;
    }

    // Citation banner (e.g., "(Citation: ...)" or "Based on ...")
    if (trimmed.startsWith('(Citation:') && trimmed.endsWith(')')) {
      blocks.push({ type: 'citation', text: trimmed.slice(1, -1) });
      i++;
      continue;
    }

    // Metadata banner: **Key:** Value | **Key:** Value
    if (trimmed.includes('**') && trimmed.includes('|') && trimmed.includes(':')) {
      const parts = trimmed.split('|').map((p) => p.trim());
      const items: { label: string; value: string }[] = [];
      let isMeta = true;

      for (const part of parts) {
        const match = part.match(/^\*\*([^*]+)\*\*:\s*(.+)$/);
        if (match) {
          items.push({ label: match[1].trim(), value: match[2].trim() });
        } else {
          isMeta = false;
          break;
        }
      }

      if (isMeta && items.length > 0) {
        blocks.push({ type: 'meta', items });
        i++;
        continue;
      }
    }

    // Headings
    if (trimmed.startsWith('# ')) {
      blocks.push({ type: 'h1', text: trimmed.slice(2).trim() });
      i++;
      continue;
    }
    if (trimmed.startsWith('## ')) {
      blocks.push({ type: 'h2', text: trimmed.slice(3).trim() });
      i++;
      continue;
    }
    if (trimmed.startsWith('### ')) {
      blocks.push({ type: 'h3', text: trimmed.slice(4).trim() });
      i++;
      continue;
    }
    if (trimmed.startsWith('#### ')) {
      blocks.push({ type: 'h4', text: trimmed.slice(5).trim() });
      i++;
      continue;
    }

    // Blockquote
    if (trimmed.startsWith('>')) {
      const quoteLines: string[] = [];
      while (i < lines.length && lines[i].trim().startsWith('>')) {
        quoteLines.push(lines[i].trim().replace(/^>\s?/, ''));
        i++;
      }
      blocks.push({ type: 'blockquote', text: quoteLines.join('\n') });
      continue;
    }

    // Markdown Table
    if (trimmed.startsWith('|') && trimmed.endsWith('|') && i + 1 < lines.length && lines[i + 1].includes('---')) {
      const headers = trimmed
        .split('|')
        .slice(1, -1)
        .map((h) => h.trim());
      i += 2; // skip header and divider line
      const rows: string[][] = [];
      while (i < lines.length && lines[i].trim().startsWith('|') && lines[i].trim().endsWith('|')) {
        const row = lines[i]
          .trim()
          .split('|')
          .slice(1, -1)
          .map((c) => c.trim());
        rows.push(row);
        i++;
      }
      blocks.push({ type: 'table', headers, rows });
      continue;
    }

    // Unordered List
    if (/^[-*]\s+/.test(trimmed)) {
      const items: string[] = [];
      while (i < lines.length && /^[-*]\s+/.test(lines[i].trim())) {
        items.push(lines[i].trim().replace(/^[-*]\s+/, ''));
        i++;
      }
      blocks.push({ type: 'ul', items });
      continue;
    }

    // Ordered List
    if (/^\d+\.\s+/.test(trimmed)) {
      const items: string[] = [];
      while (i < lines.length && /^\d+\.\s+/.test(lines[i].trim())) {
        items.push(lines[i].trim().replace(/^\d+\.\s+/, ''));
        i++;
      }
      blocks.push({ type: 'ol', items });
      continue;
    }

    // Regular Paragraph
    const pLines: string[] = [];
    while (
      i < lines.length &&
      lines[i].trim() &&
      !lines[i].trim().startsWith('#') &&
      !lines[i].trim().startsWith('```') &&
      !lines[i].trim().startsWith('>') &&
      !/^[-*]\s+/.test(lines[i].trim()) &&
      !/^\d+\.\s+/.test(lines[i].trim()) &&
      !/^(\-{3,}|\*{3,}|_{3,})$/.test(lines[i].trim())
    ) {
      pLines.push(lines[i].trim());
      i++;
    }

    if (pLines.length > 0) {
      blocks.push({ type: 'p', text: pLines.join(' ') });
    }
  }

  return blocks;
}

// ============================================================================
// Block Rendering
// ============================================================================

function renderBlock(block: Block, key: number): React.ReactNode {
  switch (block.type) {
    case 'h1':
      return (
        <h1
          key={key}
          className="text-lg md:text-xl font-bold text-slate-900 border-b border-slate-200 pb-2 mb-3 mt-4 first:mt-0 tracking-tight"
        >
          {renderInline(block.text)}
        </h1>
      );
    case 'h2':
      return (
        <h2
          key={key}
          className="text-sm md:text-base font-bold text-slate-900 border-b border-slate-100 pb-1 mb-2 mt-4 first:mt-0 tracking-tight flex items-center gap-1.5"
        >
          <span className="w-1.5 h-3.5 bg-indigo-600 rounded-full inline-block" />
          {renderInline(block.text)}
        </h2>
      );
    case 'h3':
      return (
        <h3 key={key} className="text-sm font-semibold text-slate-800 mb-1.5 mt-3 tracking-tight">
          {renderInline(block.text)}
        </h3>
      );
    case 'h4':
      return (
        <h4 key={key} className="text-xs font-semibold uppercase tracking-wider text-slate-600 mb-1 mt-2">
          {renderInline(block.text)}
        </h4>
      );
    case 'meta':
      return (
        <div
          key={key}
          className="bg-indigo-50/70 border border-indigo-100/80 rounded-xl px-3.5 py-2 my-2.5 flex flex-wrap gap-x-4 gap-y-1.5 text-xs text-indigo-950 shadow-xs"
        >
          {block.items.map((item, idx) => (
            <div key={idx} className="flex items-center gap-1">
              <span className="font-semibold text-indigo-900">{item.label}:</span>
              <span className="text-slate-700">{renderInline(item.value)}</span>
            </div>
          ))}
        </div>
      );
    case 'citation':
      return (
        <div
          key={key}
          className="bg-slate-50 border border-slate-200/80 text-slate-600 rounded-lg px-3 py-1.5 text-xs font-medium my-2 flex items-center gap-2"
        >
          <span className="w-1.5 h-1.5 rounded-full bg-emerald-500" />
          <span>{renderInline(block.text)}</span>
        </div>
      );
    case 'blockquote':
      return (
        <blockquote
          key={key}
          className="border-l-4 border-indigo-500 bg-slate-50 text-slate-700 pl-3.5 pr-3 py-2 my-2.5 rounded-r-lg italic text-xs leading-relaxed"
        >
          {renderInline(block.text)}
        </blockquote>
      );
    case 'code':
      return (
        <div key={key} className="my-3 rounded-xl overflow-hidden border border-slate-800 shadow-md">
          {block.lang && (
            <div className="bg-slate-900 px-3.5 py-1 text-[11px] font-mono font-medium text-slate-400 border-b border-slate-800">
              {block.lang}
            </div>
          )}
          <pre className="p-3.5 bg-slate-950 text-emerald-400 font-mono text-xs overflow-x-auto leading-relaxed">
            <code>{block.code}</code>
          </pre>
        </div>
      );
    case 'ul':
      return (
        <ul key={key} className="space-y-1.5 my-2 pl-1">
          {block.items.map((item, idx) => (
            <li key={idx} className="flex items-start gap-2 text-slate-700 text-sm leading-relaxed">
              <span className="w-1.5 h-1.5 bg-indigo-500 rounded-full mt-2 shrink-0" />
              <div className="flex-1">{renderInline(item)}</div>
            </li>
          ))}
        </ul>
      );
    case 'ol':
      return (
        <ol key={key} className="space-y-1.5 my-2 pl-1">
          {block.items.map((item, idx) => (
            <li key={idx} className="flex items-start gap-2 text-slate-700 text-sm leading-relaxed">
              <span className="w-5 h-5 rounded-full bg-indigo-50 text-indigo-700 text-[11px] font-bold flex items-center justify-center shrink-0 mt-0.5 border border-indigo-200/60">
                {idx + 1}
              </span>
              <div className="flex-1 pt-0.5">{renderInline(item)}</div>
            </li>
          ))}
        </ol>
      );
    case 'table':
      return (
        <div key={key} className="my-3 overflow-x-auto rounded-xl border border-slate-200 shadow-xs">
          <table className="w-full text-left text-xs border-collapse">
            <thead className="bg-slate-50 border-b border-slate-200">
              <tr>
                {block.headers.map((h, idx) => (
                  <th key={idx} className="p-2.5 font-bold text-slate-900">
                    {renderInline(h)}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100">
              {block.rows.map((row, rIdx) => (
                <tr key={rIdx} className="hover:bg-slate-50/50">
                  {row.map((cell, cIdx) => (
                    <td key={cIdx} className="p-2.5 text-slate-700">
                      {renderInline(cell)}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      );
    case 'hr':
      return <hr key={key} className="my-4 border-t border-slate-200" />;
    case 'p':
    default:
      return (
        <p key={key} className="my-1.5 text-slate-800 text-sm leading-relaxed">
          {renderInline(block.text)}
        </p>
      );
  }
}

// ============================================================================
// Inline Tokenizer & Rendering
// ============================================================================

function renderInline(text: string): React.ReactNode {
  if (!text) return null;

  // Split by inline markdown patterns:
  // **bold**, *italic*, `code`, [link](url)
  const regex = /(\*\*[^*]+\*\*|\*[^*]+\*|`[^`]+`|\[[^\]]+\]\([^)]+\))/g;
  const parts = text.split(regex);

  return parts.map((part, index) => {
    // Bold: **text**
    if (part.startsWith('**') && part.endsWith('**') && part.length >= 4) {
      return (
        <strong key={index} className="font-semibold text-slate-900">
          {part.slice(2, -2)}
        </strong>
      );
    }

    // Inline Code: `code`
    if (part.startsWith('`') && part.endsWith('`') && part.length >= 2) {
      return (
        <code
          key={index}
          className="px-1.5 py-0.5 rounded-md bg-slate-100 border border-slate-200/70 font-mono text-[12px] text-indigo-600 font-medium"
        >
          {part.slice(1, -1)}
        </code>
      );
    }

    // Italic: *text*
    if (part.startsWith('*') && part.endsWith('*') && part.length >= 2) {
      return (
        <em key={index} className="italic text-slate-700">
          {part.slice(1, -1)}
        </em>
      );
    }

    // Link: [text](url)
    const linkMatch = part.match(/^\[([^\]]+)\]\(([^)]+)\)$/);
    if (linkMatch) {
      return (
        <a
          key={index}
          href={linkMatch[2]}
          target="_blank"
          rel="noopener noreferrer"
          className="text-indigo-600 hover:text-indigo-800 underline font-medium"
        >
          {linkMatch[1]}
        </a>
      );
    }

    return part;
  });
}

