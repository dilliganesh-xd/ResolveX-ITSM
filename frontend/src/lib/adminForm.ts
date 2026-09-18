import axios from 'axios';
import type { ApiError } from './types';

export interface RequestFailure {
  message: string;
  fieldErrors: Record<string, string>;
}

/**
 * Turns an API failure into something a form can show.
 *
 * <p>Every admin screen reports errors the same way, from the same ApiError
 * shape the backend already returns - so a 409 on a duplicate email reads as the
 * server's own sentence rather than a generic apology.
 */
export function describeFailure(caught: unknown, fallback: string): RequestFailure {
  if (axios.isAxiosError<ApiError>(caught)) {
    return {
      message: caught.response?.data?.message ?? fallback,
      fieldErrors: caught.response?.data?.fieldErrors ?? {},
    };
  }
  return { message: fallback, fieldErrors: {} };
}
