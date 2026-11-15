# ComposerMediaAttachmentPanel wiring

**Status: wired** in `app/src/main/java/com/example/ui/composer/ComposerScreen.kt`.

The composer now renders `ComposerMediaAttachmentPanel` with the selected URIs and upload state. Remove and cancel both stop the active upload and clear the selected media and storage path. Retry restarts the upload for the selected URI(s).

The old inline preview/progress UI has been removed. The upload and publish flow remains in `ComposerScreen`.
