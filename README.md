# WhenApp-Android

More info coming soon! For now, join us on [Discord](https://discord.gg/sJWCF6P)!

## Release

Releases are built and uploaded to Google Play by the
[`Publish to Google Play`](.github/workflows/publish-play.yml) GitHub Actions
workflow.

Triggers:

- **Tag push** matching `v*` (e.g. `v1.2.3`) — publishes to the `internal`
  track by default.
- **Manual** (`workflow_dispatch`) — choose a track from
  `internal` / `alpha` / `beta` / `production`.

The workflow checks out the repo, sets up JDK 17 + Gradle, decodes the
upload keystore from a secret, runs `./gradlew :app:bundleProdRelease` to
produce a signed `.aab`, then uploads it to Google Play via the Play
Developer API.

### Required repository secrets

| Secret | Purpose |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64-encoded upload keystore (`base64 -w0 upload.keystore`) |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore (store) password |
| `ANDROID_KEY_ALIAS` | Key alias inside the keystore |
| `ANDROID_KEY_PASSWORD` | Password for the key entry |
| `PLAY_SERVICE_ACCOUNT_JSON` | Full JSON for a Google Play service account with `Release Manager` permissions |

The package name is read from the `app` module's `namespace`
(`tech.akpmakes.android.taskkeeper`).

### Cutting a release

```bash
git tag v1.2.3
git push origin v1.2.3
```

The workflow run will build, sign, and upload to the `internal` track. To
promote to a different track, re-run with `workflow_dispatch` and pick the
target track, or promote via the Play Console.
