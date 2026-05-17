package com.eight87.strictlykeptboy

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Round 2.28 / SOLID #8b — extracted from MainActivity.
 *
 * Holder for every SAF / ActivityResult launcher MainActivity needs.
 * Registration happens in `init`, which is called from the Activity's
 * field initializer block — i.e. before `onCreate`'s STARTED phase,
 * which is the contract `registerForActivityResult` demands.
 *
 * Each launcher has a parked `var on<Result>` callback that downstream
 * `setContent { ... }` code wires once the corresponding Compose state
 * exists. The callback signature is `(Uri) -> Unit` (the picker
 * returns a Uri? — `null` is dropped at the holder, so the callback
 * never sees null).
 *
 * The five launchers map 1:1 to the Round 2.17 SAF surfaces:
 *  - [openIcs] — pick an .ics for import (`OpenDocument`, text/calendar).
 *  - [createIcs] — create an .ics for export (`CreateDocument`, text/calendar).
 *  - [parentPicker] — pick the strictlykeptboy parent folder (`OpenDocumentTree`).
 *  - [adoptPicker] — pick a folder to adopt (`OpenDocumentTree`, no parent switch).
 *  - [restoreArchivePicker] — pick a `.skb-backup.tar.gz` for restore (`OpenDocument`).
 *  - [exportArchivePicker] — create a `.skb-backup.tar.gz` for export (`CreateDocument`).
 */
class MainActivityLaunchers(activity: ComponentActivity) {

    /** Set before calling [openIcs] launch; invoked with the picked Uri. */
    var onIcsPicked: ((Uri) -> Unit)? = null
    /** Set before calling [createIcs] launch; invoked with the destination Uri. */
    var onIcsCreated: ((Uri) -> Unit)? = null
    /** Set before calling [parentPicker] launch; invoked with the picked tree Uri. */
    var onParentPicked: ((Uri) -> Unit)? = null
    /** Set before calling [adoptPicker] launch; invoked with the picked tree Uri. */
    var onAdoptPicked: ((Uri) -> Unit)? = null
    /** Set before calling [restoreArchivePicker] launch; invoked with the source Uri. */
    var onRestoreArchivePicked: ((Uri) -> Unit)? = null
    /** Set before calling [exportArchivePicker] launch; invoked with the destination Uri. */
    var onExportArchiveCreated: ((Uri) -> Unit)? = null

    val openIcs = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { onIcsPicked?.invoke(it) } }

    val createIcs = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/calendar")
    ) { uri: Uri? -> uri?.let { onIcsCreated?.invoke(it) } }

    val parentPicker = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? -> uri?.let { onParentPicked?.invoke(it) } }

    val adoptPicker = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? -> uri?.let { onAdoptPicked?.invoke(it) } }

    val restoreArchivePicker = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let { onRestoreArchivePicked?.invoke(it) } }

    val exportArchivePicker = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/gzip")
    ) { uri: Uri? -> uri?.let { onExportArchiveCreated?.invoke(it) } }
}
