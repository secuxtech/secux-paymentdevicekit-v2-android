# Credential handling

## Remaining payment credential cleanup

- Removed committed test payment keys and alternative-key comments. Payment/activation examples start without credentials and reject missing or invalid configuration before the key-dependent operation.
- Configure the intended test terminal and a fresh 32-byte UTF-8 payment key at runtime using `configurePaymentDemo` (or `configureActivation` in the firmware-update tool). Match the key to the intended test device. Never substitute an arbitrary shared key or embed production keys in a mobile app.

This cleanup removes the identified values/artifacts from the current branch. It does not revoke server accounts, rotate deployed device keys, rewrite Git history, remove old tags/releases, or automatically resolve scanner alerts. Treat previously published usable credentials as exposed and have their owners revoke or rotate them. Use new, narrowly scoped test credentials if development resumes.
