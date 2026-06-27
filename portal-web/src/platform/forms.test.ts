import { describe, expect, it } from 'vitest';
import { loginSchema, passwordChangeSchema, resetPasswordSchema } from './forms';

describe('platform form schemas', () => {
  it('validates login credentials declaratively', () => {
    expect(loginSchema.safeParse({ username: '', password: '' }).success).toBe(false);
    expect(loginSchema.safeParse({ username: 'admin', password: 'Password123' }).success).toBe(true);
  });

  it('rejects weak or mismatched password changes', () => {
    expect(passwordChangeSchema.safeParse({
      confirmPassword: 'NewPassword123',
      newPassword: 'NewPassword123',
      oldPassword: 'NewPassword123'
    }).success).toBe(false);
    expect(passwordChangeSchema.safeParse({
      confirmPassword: 'NewPassword123',
      newPassword: 'NewPassword123',
      oldPassword: 'OldPassword123'
    }).success).toBe(true);
  });

  it('validates reset password confirmation', () => {
    expect(resetPasswordSchema.safeParse({
      confirmPassword: 'Password1234',
      newPassword: 'Password4321',
      username: 'tester'
    }).success).toBe(false);
    expect(resetPasswordSchema.safeParse({
      confirmPassword: 'Password1234',
      newPassword: 'Password1234',
      username: 'tester'
    }).success).toBe(true);
  });
});
