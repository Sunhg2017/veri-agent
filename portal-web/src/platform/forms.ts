import { z } from 'zod';
import { translate } from './i18n';

export const loginSchema = z.object({
  password: z.string().min(1, translate('auto.k2009')),
  username: z.string().trim().min(1, translate('auto.k2009'))
});

export const passwordChangeSchema = z.object({
  confirmPassword: z.string().min(1, translate('auto.k2010')),
  newPassword: z.string().min(10, translate('auto.k2011')),
  oldPassword: z.string().min(1, translate('auto.k2010'))
}).superRefine((value, ctx) => {
  if (value.newPassword !== value.confirmPassword) {
    ctx.addIssue({
      code: 'custom',
      message: translate('auto.k2012'),
      path: ['confirmPassword']
    });
  }
  if (value.oldPassword === value.newPassword) {
    ctx.addIssue({
      code: 'custom',
      message: translate('auto.k2013'),
      path: ['newPassword']
    });
  }
});

export const resetPasswordSchema = z.object({
  confirmPassword: z.string().min(1, translate('auto.k2014')),
  newPassword: z.string().min(10, translate('auto.k2015')),
  username: z.string().min(1, translate('auto.k2016'))
}).superRefine((value, ctx) => {
  if (value.newPassword !== value.confirmPassword) {
    ctx.addIssue({
      code: 'custom',
      message: translate('auto.k2017'),
      path: ['confirmPassword']
    });
  }
});

export type LoginFormValues = z.infer<typeof loginSchema>;
export type PasswordChangeFormValues = z.infer<typeof passwordChangeSchema>;
export type ResetPasswordFormValues = z.infer<typeof resetPasswordSchema>;
