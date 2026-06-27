import { z } from 'zod';

export const loginSchema = z.object({
  password: z.string().min(1, '请输入账号和密码'),
  username: z.string().trim().min(1, '请输入账号和密码')
});

export const passwordChangeSchema = z.object({
  confirmPassword: z.string().min(1, '请填写完整密码信息'),
  newPassword: z.string().min(10, '新密码至少 10 位'),
  oldPassword: z.string().min(1, '请填写完整密码信息')
}).superRefine((value, ctx) => {
  if (value.newPassword !== value.confirmPassword) {
    ctx.addIssue({
      code: 'custom',
      message: '两次输入的新密码不一致',
      path: ['confirmPassword']
    });
  }
  if (value.oldPassword === value.newPassword) {
    ctx.addIssue({
      code: 'custom',
      message: '新密码不能与旧密码相同',
      path: ['newPassword']
    });
  }
});

export const resetPasswordSchema = z.object({
  confirmPassword: z.string().min(1, '请填写新密码和确认密码'),
  newPassword: z.string().min(10, '密码至少 10 位'),
  username: z.string().min(1, '账号不能为空')
}).superRefine((value, ctx) => {
  if (value.newPassword !== value.confirmPassword) {
    ctx.addIssue({
      code: 'custom',
      message: '两次输入的密码不一致',
      path: ['confirmPassword']
    });
  }
});

export type LoginFormValues = z.infer<typeof loginSchema>;
export type PasswordChangeFormValues = z.infer<typeof passwordChangeSchema>;
export type ResetPasswordFormValues = z.infer<typeof resetPasswordSchema>;
