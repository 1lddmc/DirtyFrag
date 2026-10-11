# Changelog

## 1.15

### Movable card UI
- The four cards are a stack you can rearrange. Hold one and it snaps under your finger, drop it where
  you want it and the rest settle around it. The order is kept, so it survives a reboot and a re-run
  of the exploit.
- Every card except SU Manager can be hidden with the minus chip on its edge - the SU Manager card
  refuses to be hidden so the stack can never be emptied by mistake - and **Menu -> Reset layout**
  brings the whole stack back.
- The advanced log belongs to the "Show advanced log" card: it hangs off that card's bottom edge,
  travels with it, and takes its room with it the moment the card is lifted or hidden.

### Engine - the run no longer dies at the verify stage (issue #5)
- The kernel module's usermodehelper command is a `sh -c` chain that ended in
  `rmmod oplus_security_guard`. Those three `rmmod` calls are Oppo/OnePlus only, so on every other
  kernel the last command failed, `sh -c` returned its exit code and `call_usermodehelper_exec`
  reported `returned 256` - the bootstrap never started and the run stopped at the verify stage. The
  chain now ends in `exit 0`. Found and verified by @BrokenByteOfCode on a Samsung A07 4G (SM-A075F,
  android16-6.12).
- The eight embedded kernel modules were rebuilt from this source for this release, so the fix reaches
  devices through the APK.

## 1.11

Based on upstream DFRoot 4.1 (upstream 3.0 - 4.1 merged into this fork).

### Engine - upstream DFRoot 4.0 merged
- OPPO/OnePlus bypasses added to the kernel module
- the OPPO security modules (`oplus_secure_harden`, `oplus_security_keventupload`,
  `oplus_security_guard`) are `rmmod`-ed before the bootstrap handoff
- unneeded kprobes removed from the module
- upstream v4.0 release work: compile cleanup, minified release build, smaller APK
### Engine - upstream DFRoot 4.1 merged
- soft reboot now works with every KernelSU derivative. `dfroot.ko` keeps its kprobes in static
  globals and gains a real `module_exit`, so the bootstrap can `rmmod dfroot` once late-load is
  done instead of the module self-unloading by failing its own init; the ksud `soft-reboot` is
  execed in the `u:r:ksu:s0` context only after the late-load process has exited
- the manager picker lists only apps that actually ship `libksud.so` - this fork already did that,
  so the behaviour is unchanged
- the eight embedded kernel modules were rebuilt from the 4.1 `lkm/dfroot.c` (one per KMI, built by
  this repo's CI), so the shipped modules match the marker table the app reads

### DirtyFrag
- Every run log starts with a device banner - manufacturer, model, Android version, security patch
  level and kernel version - so a pasted log can be triaged without asking follow-up questions
- The log header is a plain "SYSTEM" line instead of a firmware build token
- The launcher icon is a vector (adaptive foreground, background and monochrome) instead of five
  PNG densities: the APK is 2.24 MB instead of 2.91 MB
- The progress bar is strictly linear: the run bar reaches 100% when the module is in, and only
  then does the verification bar start moving
- Build moved to AGP 9.4.0 with compile/target SDK 37
- BakaSU is listed as a manager for non-Samsung devices
- zandatsu07's KernelSU-Next is listed as a manager for Samsung devices

### Samsung devices: which manager to install
Samsung firmware enforces its own KDP/DEFEX credential handling, so a stock KernelSU build cannot
take root there. Use one of the Samsung builds of KernelSU or KernelSU-Next instead:

- [diabl0w's KernelSU](https://github.com/diabl0w/KernelSU/releases/tag/samsung-v1.0)
- [zandatsu07's KernelSU-Next](https://github.com/zandatsu07/KernelSU-Next/releases/latest)

Install it, pick it in DirtyFrag, and turn on Auto reboot if you want the soft reboot.

### Not in this release: the BakaSU manager
During this cycle the Samsung KDP/DEFEX handling was ported into BakaSU (ex-ReSukiSU) so that
Samsung devices could use it as the manager. The port does not work yet: on the test device
(SM-S931B) the device breaks right after the grant, and a control build with the port compiled out
fails the same way, so the fault is not the port itself. It is not part of 1.11 and will be merged
later once it is fixed. Until then Samsung devices must use one of the Samsung builds linked
above.

## 1.10

Based on upstream DFRoot 3.3 (upstream 3.0 - 3.3 merged into this fork).

### Engine
- The exploit is no longer a JNI library: it runs as a staged executable and hands off to a
  bootstrap binary. The bootstrap reads the app's device-protected settings, adopts the zygote
  environment, sets partitions read-only, optionally disables all modules, and only then starts the
  SU daemon
- Runtime kallsyms resolution of `kern_path`, `invalidate_inode_pages2` and `path_put`, which fixes
  namespace violations on some Android 13 kernels
- `__nocfi` annotations on the kernel module - the upstream issue #68 class of crashes on kernels
  with control-flow integrity
- libc++ restore-ordering fix
- `ksud` is staged from the selected SU manager instead of being bundled with the app. The APK is
  2.9 MB instead of 6.5 MB

### DirtyFrag
- Pick your SU manager inside the app; Run stays disabled until one is selected
- KernelSU Modules and soft reboot now work without root
- Autorun keeps its Expert-mode gate and its failed-run interlock
- SU-manager launcher button: a circle while a run is in flight, opening into a "Manager" pill once
  root is verified
- Save the log to Downloads from the circle beside the run pill, available after any finished run
- Neutral grey press highlights throughout, and the Material purple tints are gone - including the
  purple flash for a moment on cold start
- The eight prebuilt kernel modules are committed so that a clone of this repository can build

## 1.08
- KernelSU Modules toggle
- Clearer status text and a refresh when returning to the app

## Earlier
See the release notes on the releases page.
