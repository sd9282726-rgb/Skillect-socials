# Skillect Supabase Auth configuration

## Signup: no email verification

Skillect signup must create an authenticated session immediately. In the Supabase Dashboard for project `chohswyxsckpwyzlbzav`:

1. Open **Authentication → Providers → Email**.
2. Turn **Confirm email** OFF.
3. Save the provider settings.

This is a hosted Auth setting, not a database migration or an app setting. The available Supabase project connector does not expose the hosted Auth provider configuration endpoint, so this setting still has to be changed in the Dashboard.

With confirmations disabled, Supabase confirms new email/password users at signup and returns a session. The app deliberately rejects a signup response without a session instead of pretending the account is ready or asking for an OTP.

## Forgot password: send an OTP, not a link

In **Authentication → Email Templates → Reset Password**:

- Subject: `Your Skillect password reset code`
- Replace the template body with:

```html
<h2>Reset your Skillect password</h2>
<p>Enter this one-time code in the Skillect app:</p>
<p style="font-size:30px;font-weight:700;letter-spacing:6px">{{ .Token }}</p>
<p>This code expires soon. If you did not request a password reset, ignore this email.</p>
```

Do not use `{{ .ConfirmationURL }}` in the Reset Password template. That variable generates a clickable recovery link; `{{ .Token }}` renders the one-time code.

The Android app requests `POST /auth/v1/recover`, accepts the six-digit code, verifies it with `POST /auth/v1/verify` using `type=recovery`, and then updates the password using the returned recovery access token.

Supabase's official email template documentation describes `{{ .Token }}` as the OTP variable and `{{ .ConfirmationURL }}` as the link variable: https://supabase.com/docs/guides/auth/auth-email-templates
