import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ReportForm } from './ReportForm';
import { strings } from '../i18n/strings';

let fetchSpy: ReturnType<typeof vi.spyOn>;

function renderForm(onSubmitted = vi.fn()) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  render(
    <QueryClientProvider client={client}>
      <ReportForm onSubmitted={onSubmitted} />
    </QueryClientProvider>,
  );
  return { onSubmitted };
}

function submitButton() {
  return screen.getByRole('button', { name: new RegExp(strings.form.submit) });
}

beforeEach(() => {
  fetchSpy = vi.spyOn(globalThis, 'fetch');
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe('ReportForm', () => {
  it('refuses to submit nothing, and counts what is typed', async () => {
    renderForm();

    expect(submitButton()).toBeDisabled();

    await userEvent.type(screen.getByLabelText(strings.form.label), 'Ankara');

    expect(screen.getByText(new RegExp(strings.form.charCount(6)))).toBeInTheDocument();
    expect(submitButton()).toBeEnabled();
  });

  it('treats whitespace as nothing', async () => {
    renderForm();

    await userEvent.type(screen.getByLabelText(strings.form.label), '    ');

    expect(submitButton()).toBeDisabled();
  });

  it('sends the text and hands the receipt id upwards', async () => {
    fetchSpy.mockResolvedValue({
      ok: true,
      status: 201,
      json: async () => ({ id: 'abc123', submittedAt: '2026-08-10T06:56:28Z' }),
    } as Response);
    const { onSubmitted } = renderForm();

    await userEvent.type(screen.getByLabelText(strings.form.label), 'İzmir’de deprem');
    await userEvent.click(submitButton());

    // FR-19: the id is what the result is read with; the response carries
    // nothing else worth showing.
    await vi.waitFor(() => expect(onSubmitted).toHaveBeenCalledWith('abc123'));
    const [, init] = fetchSpy.mock.calls[0] ?? [];
    expect((init as RequestInit).body).toBe(JSON.stringify({ text: 'İzmir’de deprem' }));
  });

  it('clears the text area only after the text is safely stored', async () => {
    fetchSpy.mockResolvedValue({
      ok: true,
      status: 201,
      json: async () => ({ id: 'abc123', submittedAt: '2026-08-10T06:56:28Z' }),
    } as Response);
    renderForm();
    const field = screen.getByLabelText(strings.form.label);

    await userEvent.type(field, 'Bir metin');
    await userEvent.click(submitButton());

    await vi.waitFor(() => expect(field).toHaveValue(''));
  });

  it('keeps the text when the server rejects it, and says why in Turkish', async () => {
    fetchSpy.mockResolvedValue({
      ok: false,
      status: 400,
      json: async () => ({ code: 'report.text.blank', detail: 'must not be empty', status: 400 }),
    } as Response);
    renderForm();
    const field = screen.getByLabelText(strings.form.label);

    await userEvent.type(field, 'x');
    await userEvent.click(submitButton());

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(strings.errors.byCode['report.text.blank'] as string);
    // Losing what the user typed because the server said no would be the worst
    // possible response to a rejection.
    expect(field).toHaveValue('x');
  });

  it('does not show the server’s English wording', async () => {
    const english = 'Incident report text must be at most 10000 characters, got 10001.';
    fetchSpy.mockResolvedValue({
      ok: false,
      status: 400,
      json: async () => ({ code: 'report.text.too-long', detail: english, status: 400 }),
    } as Response);
    renderForm();

    await userEvent.type(screen.getByLabelText(strings.form.label), 'uzun');
    await userEvent.click(submitButton());

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent(strings.errors.byCode['report.text.too-long'] as string);
    expect(alert).not.toHaveTextContent(english);
  });

  it('clears a previous rejection as soon as the user edits the text', async () => {
    fetchSpy.mockResolvedValue({
      ok: false,
      status: 400,
      json: async () => ({ code: 'report.text.blank', status: 400 }),
    } as Response);
    renderForm();
    const field = screen.getByLabelText(strings.form.label);

    await userEvent.type(field, 'x');
    await userEvent.click(submitButton());
    await screen.findByRole('alert');

    await userEvent.type(field, 'y');

    // A message about the previous attempt has nothing to say about the text
    // now on screen.
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('locks the button while the submission is in flight', async () => {
    let release: (value: unknown) => void = () => {};
    fetchSpy.mockImplementation(
      () =>
        new Promise((resolve) => {
          release = resolve;
        }),
    );
    renderForm();

    await userEvent.type(screen.getByLabelText(strings.form.label), 'metin');
    await userEvent.click(submitButton());

    // Without the lock the same text could be stored twice (FR-18).
    await vi.waitFor(() =>
      expect(screen.getByRole('button', { name: strings.form.submitting })).toBeDisabled(),
    );

    release({ ok: true, status: 201, json: async () => ({ id: 'x', submittedAt: 'now' }) });
  });
});
