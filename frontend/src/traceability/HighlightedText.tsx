import { strings } from '../i18n/strings';
import { toSegments } from './highlight';
import type { ExtractedKeyword, KeywordRole } from '../api/types';

const ROLES: KeywordRole[] = ['DATE', 'PROVINCE', 'EVENT_TYPE', 'METRIC'];

/**
 * The raw text, unchanged, with the words the extractor matched marked on it
 * (FR-02, FR-26).
 *
 * Nothing is added to the text itself - not a marker, not a label, not a hidden
 * annotation. This screen exists to show what was stored, and text selected from
 * it has to be that text: a screen-reader-only "(province)" inside each
 * highlight would read well and copy badly.
 *
 * What each highlight means is therefore carried outside the text: a legend
 * above it, a tooltip on it, and an underline style per role - so the four roles
 * remain distinguishable without telling four pale shades apart (NFR-16).
 * Whitespace is preserved rather than collapsed, because runs of spaces and line
 * breaks are part of what was stored.
 */
export function HighlightedText({
  text,
  keywords,
}: {
  text: string;
  keywords: ExtractedKeyword[];
}) {
  const present = ROLES.filter((role) => keywords.some((keyword) => keyword.role === role));

  return (
    <>
      {present.length > 0 && (
        <ul className="keyword-legend">
          {present.map((role) => (
            <li key={role}>
              <span className="keyword" data-role={role} aria-hidden="true">
                &nbsp;&nbsp;
              </span>{' '}
              {strings.detail.keywordRole[role]}
            </li>
          ))}
        </ul>
      )}
      <p className="raw-text">
        {toSegments(text, keywords).map((segment, index) =>
          segment.roles.length === 0 ? (
            <span key={index}>{segment.text}</span>
          ) : (
            <mark
              key={index}
              className="keyword"
              data-role={segment.roles[0]}
              title={segment.roles.map((role) => strings.detail.keywordRole[role]).join(', ')}
            >
              {segment.text}
            </mark>
          ),
        )}
      </p>
    </>
  );
}
