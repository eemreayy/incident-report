import { ApiError } from '../api/problem';
import { strings } from './strings';

/**
 * Turns a failure into something a user can read.
 *
 * This lives on the i18n side rather than in the API layer on purpose. The
 * server's `detail` is English, so it is not showable as-is; `code` is the
 * machine-readable half of the error contract and is what gets translated here.
 * An unknown code falls back to a generic sentence rather than to the English
 * detail - a user should never be shown a sentence written for a developer.
 */
export function messageForError(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return strings.errors.unknown;
  }
  const known = strings.errors.byCode[error.code];
  return known ?? strings.errors.unknown;
}
