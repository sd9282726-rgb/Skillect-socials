# Wire ComposerMediaAttachmentPanel into ComposerScreen

The panel is already on the branch (`ComposerMediaAttachmentPanel.kt`).

In `ComposerScreen.kt`, replace the block starting at:
`// Media Previews & Upload Progress / Cancel / Retry`
through the end of the `when (val state = uploadState) { ... }` block
(just before `if (showLinkFields)`)

with:

```kotlin
                ComposerMediaAttachmentPanel(
                    selectedUris = selectedUris.toList(),
                    uploadState = uploadState,
                    onRemove = {
                        uploadJob?.cancel()
                        selectedUris.clear()
                        videoStoragePath = null
                        uploadState = UploadProgressState.Idle
                    },
                    onCancelUpload = {
                        uploadJob?.cancel()
                        selectedUris.clear()
                        videoStoragePath = null
                        uploadState = UploadProgressState.Idle
                    },
                    onRetryUpload = { startUploadForUris(selectedUris.toList()) },
                )
```

Until this is wired, the new panel compiles as an unused file; the old inline media UI still runs.
