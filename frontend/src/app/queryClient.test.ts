import { describe, expect, it } from 'vitest';
import { createQueryClient } from './queryClient';

describe('createQueryClient', () => {
  it('does not refetch on window focus, because the stream is the change signal', () => {
    const defaults = createQueryClient().getDefaultOptions().queries;

    expect(defaults?.refetchOnWindowFocus).toBe(false);
  });

  it('hands out a fresh client per call so tests cannot leak cache into each other', () => {
    expect(createQueryClient()).not.toBe(createQueryClient());
  });
});
