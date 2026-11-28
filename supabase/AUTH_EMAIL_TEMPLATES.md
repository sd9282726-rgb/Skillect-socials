# Skillect email OTP templates

Skillect's Android client verifies signup and password-reset codes in-app. The hosted Supabase Auth email templates must contain the OTP token instead of a confirmation URL.

In Supabase Dashboard → Authentication → Email Templates:

## Confirm signup

Subject: `Your Skillect verification code`

HTML:
```html
<h2>Verify your Skillect account</h2>
<p>Enter this 6-digit code in the Skillect app to activate your account:</p>
<p style="font-size:28px;font-weight:700;letter-spacing:6px">{{ .Token }}</p>
<p>This code expires soon. If you did not create a Skillect account, ignore this email.</p>
```

## Reset password

Subject: `Your Skillect password reset code`

HTML:
```html
<h2>Reset your Skillect password</h2>
<p>Enter this 6-digit code in the Skillect app:</p>
<p style="font-size:28px;font-weight:700;letter-spacing:6px">{{ .Token }}</p>
<p>If you did not request a password reset, ignore this email.</p>
```

Do not use `{{ .ConfirmationURL }}` in these two templates; that sends a clickable link. The app verifies signup with `type=email` and recovery with `type=recovery`.

The template itself is hosted Auth configuration and is not stored in the app repository. Apply both templates in the Dashboard before testing signup and password recovery.
