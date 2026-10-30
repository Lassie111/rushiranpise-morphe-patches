package app.template.patches.googlephotos

import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.patch.bytecodePatch
import app.template.patches.shared.Constants.GOOGLE_PHOTOS_COMPATIBILITY
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

// The backup-settings "unlimited storage" label is driven by
// StorageQuotaInfo.unlimitedStorageQuota (the first field of the class). That flag
// is resolved from the account's Google Play Services quota sync, NOT from the
// Pixel feature spoof: `Spoof features` makes Photos believe it is a Pixel XL, but
// hasSystemFeature/Build spoofing never touches this field. On genuine Pixel
// hardware running the original package against real Play Services (i.e. a root
// mount install), the backend reports the device's real quota policy, the flag is
// false, and the label is hidden - even though the spoof is otherwise working and
// the account still has unlimited storage. Non-root installs use a renamed package
// talking to a GmsCore, so they never receive the genuine-device quota and the
// label shows, which is also why it shows on non-Pixel hardware.
//
// Fix: force unlimitedStorageQuota to true at the single StorageQuotaInfo
// constructor. Every instance (the live quota store and the Parcelable path) is
// created through it, so every reader - the storage label, the usage bar, and the
// backup promos - then treats storage as unlimited.
@Suppress("unused")
val googlePhotosUnlimitedStorageLabelPatch = bytecodePatch(
    name = "Show unlimited storage label",
    description = "Forces Google Photos to show the unlimited-storage label. Genuine Pixel " +
        "hardware otherwise hides it because the quota flag comes from Play Services, not the spoof.",
    default = true,
) {
    compatibleWith(GOOGLE_PHOTOS_COMPATIBILITY)

    execute {
        val method = StorageQuotaInfoConstructorFingerprint.method
        val implementation = method.implementation ?: return@execute

        // unlimitedStorageQuota is the first field of the class, so it is the first
        // boolean stored in the constructor. Target that store and read the register
        // it copies from, rather than computing it from parameter widths.
        val instructions = implementation.instructions.toList()
        val storeIndex = instructions.indexOfFirst { it.opcode == Opcode.IPUT_BOOLEAN }
        if (storeIndex < 0) return@execute
        val valueRegister = (instructions[storeIndex] as TwoRegisterInstruction).registerA

        // Overwrite the value with true immediately before it is stored.
        method.addInstructions(storeIndex, "const/16 v$valueRegister, 0x1")
    }
}
