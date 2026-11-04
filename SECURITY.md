# Security Policy

## Supported versions

The latest release is what I maintain. Older tags get security fixes only when the
patch backports cleanly.

## Reporting a vulnerability

Please do **not** open a public issue for anything that could be abused in its
current state.

- Use the *Report a vulnerability* form on the repository page
  (Security → Report a vulnerability), or email the address on my GitHub profile.
- I aim to acknowledge within 48 hours and land a fix or a written plan within 7 days.
- I am a student running these projects in my spare time. I will not threaten or
  require anything from a reporter — I only ask for reasonable notice before any
  public write-up so deployments can upgrade first.

## What is out of scope

Anything that only hurts a server operator who already holds the secrets — a leaked
`ADMIN_PASSWORD`, an operator pasting their own session cookie into a public issue,
a reverse proxy that ignores the allowlist — is a deployment mistake rather than a
flaw here. Tell me anyway and I will make the docs louder about it.
