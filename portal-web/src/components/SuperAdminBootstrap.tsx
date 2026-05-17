import { AlertCircle, CheckCircle2, Loader2, ShieldCheck } from 'lucide-react';
import { FormEvent, useMemo, useState } from 'react';
import { ApiResult, BootstrapPayload, BootstrapResponse, bootstrapSuperAdmin } from '../lib/api';

const initialPayload: BootstrapPayload = {
  bootstrap_token: '',
  username: '',
  password: '',
  display_name: '',
  email: ''
};

type ValidationErrors = Partial<Record<keyof BootstrapPayload, string>>;

function validate(payload: BootstrapPayload): ValidationErrors {
  const errors: ValidationErrors = {};

  if (!payload.bootstrap_token.trim()) {
    errors.bootstrap_token = '请输入初始化令牌';
  }
  if (!payload.username.trim()) {
    errors.username = '请输入用户名';
  } else if (payload.username.trim().length < 3) {
    errors.username = '用户名至少 3 个字符';
  }
  if (!payload.password) {
    errors.password = '请输入密码';
  } else if (payload.password.length < 8) {
    errors.password = '密码至少 8 个字符';
  }
  if (!payload.display_name.trim()) {
    errors.display_name = '请输入显示名称';
  }
  if (!payload.email.trim()) {
    errors.email = '请输入邮箱';
  } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(payload.email.trim())) {
    errors.email = '邮箱格式不正确';
  }

  return errors;
}

export default function SuperAdminBootstrap() {
  const [payload, setPayload] = useState<BootstrapPayload>(initialPayload);
  const [touched, setTouched] = useState<ValidationErrors>({});
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<ApiResult<BootstrapResponse> | null>(null);

  const errors = useMemo(() => validate(payload), [payload]);
  const hasErrors = Object.keys(errors).length > 0;

  function updateField(field: keyof BootstrapPayload, value: string) {
    setPayload((current) => ({
      ...current,
      [field]: value
    }));
    setResult(null);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setTouched(errors);

    if (hasErrors) {
      return;
    }

    setLoading(true);
    const nextResult = await bootstrapSuperAdmin({
      ...payload,
      bootstrap_token: payload.bootstrap_token.trim(),
      username: payload.username.trim(),
      display_name: payload.display_name.trim(),
      email: payload.email.trim()
    });
    setResult(nextResult);
    setLoading(false);
  }

  return (
    <section className="panel bootstrap-panel" aria-labelledby="bootstrap-title">
      <div className="panel-header">
        <div>
          <p className="eyebrow">初始化</p>
          <h2 id="bootstrap-title">超级管理员初始化</h2>
        </div>
        <div className="section-icon">
          <ShieldCheck size={22} />
        </div>
      </div>

      <form className="form-grid" onSubmit={handleSubmit} noValidate>
        <label className="field field-wide">
          <span>Bootstrap Token</span>
          <input
            name="bootstrap_token"
            type="password"
            autoComplete="off"
            value={payload.bootstrap_token}
            onBlur={() => setTouched((current) => ({ ...current, bootstrap_token: errors.bootstrap_token }))}
            onChange={(event) => updateField('bootstrap_token', event.target.value)}
            aria-invalid={Boolean(touched.bootstrap_token)}
          />
          {touched.bootstrap_token && <small>{touched.bootstrap_token}</small>}
        </label>

        <label className="field">
          <span>用户名</span>
          <input
            name="username"
            type="text"
            autoComplete="username"
            value={payload.username}
            onBlur={() => setTouched((current) => ({ ...current, username: errors.username }))}
            onChange={(event) => updateField('username', event.target.value)}
            aria-invalid={Boolean(touched.username)}
          />
          {touched.username && <small>{touched.username}</small>}
        </label>

        <label className="field">
          <span>密码</span>
          <input
            name="password"
            type="password"
            autoComplete="new-password"
            value={payload.password}
            onBlur={() => setTouched((current) => ({ ...current, password: errors.password }))}
            onChange={(event) => updateField('password', event.target.value)}
            aria-invalid={Boolean(touched.password)}
          />
          {touched.password && <small>{touched.password}</small>}
        </label>

        <label className="field">
          <span>显示名称</span>
          <input
            name="display_name"
            type="text"
            autoComplete="name"
            value={payload.display_name}
            onBlur={() => setTouched((current) => ({ ...current, display_name: errors.display_name }))}
            onChange={(event) => updateField('display_name', event.target.value)}
            aria-invalid={Boolean(touched.display_name)}
          />
          {touched.display_name && <small>{touched.display_name}</small>}
        </label>

        <label className="field">
          <span>邮箱</span>
          <input
            name="email"
            type="email"
            autoComplete="email"
            value={payload.email}
            onBlur={() => setTouched((current) => ({ ...current, email: errors.email }))}
            onChange={(event) => updateField('email', event.target.value)}
            aria-invalid={Boolean(touched.email)}
          />
          {touched.email && <small>{touched.email}</small>}
        </label>

        <div className="form-actions field-wide">
          <button className="primary-button" type="submit" disabled={loading}>
            {loading ? <Loader2 size={17} className="spin" /> : <ShieldCheck size={17} />}
            <span>{loading ? '提交中' : '初始化超级管理员'}</span>
          </button>
        </div>
      </form>

      {result?.ok && (
        <div className="result-banner success">
          <CheckCircle2 size={18} />
          <div>
            <strong>初始化成功</strong>
            <span>Trace ID: {result.traceId ?? result.data.trace_id ?? '未返回'}</span>
          </div>
        </div>
      )}

      {result && !result.ok && (
        <div className="result-banner error">
          <AlertCircle size={18} />
          <div>
            <strong>{result.message}</strong>
            <span>Trace ID: {result.traceId ?? '未返回'}</span>
          </div>
        </div>
      )}
    </section>
  );
}
