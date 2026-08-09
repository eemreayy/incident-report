import { describe, expect, it } from 'vitest';
import { ApiError } from '../api/problem';
import { messageForError } from './errorMessages';
import { strings } from './strings';

describe('messageForError', () => {
  it('translates a known error code into Turkish', () => {
    const message = messageForError(new ApiError('report.text.blank', 400, 'must not be empty'));

    expect(message).toBe(strings.errors.byCode['report.text.blank']);
  });

  it('never shows the server’s English detail to the user', () => {
    const detail = 'Incident report text must not be empty.';

    const message = messageForError(new ApiError('some.unmapped.code', 400, detail));

    expect(message).not.toContain(detail);
    expect(message).toBe(strings.errors.unknown);
  });

  it('has a message for an unreachable server, which no server can send', () => {
    expect(messageForError(new ApiError('network.unreachable', 0, null))).toBe(
      strings.errors.byCode['network.unreachable'],
    );
  });

  it('copes with something that is not an ApiError at all', () => {
    expect(messageForError(new TypeError('boom'))).toBe(strings.errors.unknown);
    expect(messageForError(undefined)).toBe(strings.errors.unknown);
  });
});
