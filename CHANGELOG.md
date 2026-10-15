# Changelog

## Unreleased

- Watching [chisewaguri/DFRoot](https://github.com/chisewaguri/DFRoot/tree/upstream-standalone): it drops
  the separate bootstrap binary and the staged copy of ksud, and has the kernel module exec the selected
  manager's own `libksud.so`, passing the package name and both switches as insmod parameters. That is
  worth taking once it is rebased onto upstream 4.5 and lands upstream - on its own the branch re-adds the
  `rmmod oplus_*` chain behind issue #21, and it only writes the `dfm0`/`dfm1` markers our progress bar
  does not read.

### Managers
- zandatsu07's KernelSU-Next is no longer listed as a Samsung manager, following upstream: it only
  worked on S23 and below with kernel 5.15, so it is dropped from the list until it works on every
  Samsung device. diabl0w's KernelSU stays the Samsung manager and the non-Samsung list is unchanged.

## 1.16

Based on upstream DFRoot 4.5 (upstream 4.2 - 4.5 merged into this fork).

### Engine - non-Samsung and MTK devices stop rebooting (issue #21)
- The kernel module no longer runs `rmmod oplus_secure_harden`, `rmmod oplus_security_keventupload` and
  `rmmod oplus_security_guard`. On an Oppo/OnePlus MTK kernel those modules sit on a notifier chain, so
  removing them made the vendor security daemon hit a user-space `BUG()` and the device rebooted to a
  black screen on every run - reported on a OnePlus Ace6 (VT0X5AOWUU7U/ocloud-cn, N123, ColorOS
  16.0.7.101). Their permission checks are now neutralised in-kernel instead: the module kprobes
  `defex_enforce_permission`, `keventupload_permission` and `oplus_pkey_mprotect_enforce_permission`,
  patches the probed instruction into `mov w0, #0; ret` with `aarch64_insn_patch_text`, walks the
  hardening notifier blocks out of the chain, and puts the original instructions back afterwards.
- `call_usermodehelper_exec` is called with `UMH_WAIT_EXEC` instead of `UMH_WAIT_PROC`, so the module no
  longer waits for the helper's exit status. That wait is the other half of the MTK reboot, and the status
  it reported is what a Samsung saw as `returned 256` in 1.15 (issue #5). The helper command is now simply
  `touch /dev/dfm0; exec /data/user_de/0/df.root/bootstrap`, so the `exit 0` workaround from 1.15 is gone -
  there is no longer a failing command at the end of the chain to mask.

### Engine - the right kernel module gets picked more often
- **Code cave fallback.** When the loader found no executable code cave it failed with a bare
  `[-] Failed to find a suitable code cave` and no detail. It now reports the candidates it examined and
  falls back to the start of the first writable and executable mapping it can find, so devices that died
  here can run.
- **BTI/PAC kernels.** On a kernel built with branch target identification and pointer authentication the
  payload used to abort on its own preamble before it started. The preamble handles those instructions
  properly now (upstream `I hate BTI/PAC`).

### App
- The run banner in the advanced log opens with `app version:`, so a pasted log says which build produced
  it; it reads the same `BuildConfig.VERSION_NAME` as the toolbar pill.
- `run()` is `synchronized` and returns early when `/dev/df` already exists, so the boot autorun racing a
  manual tap no longer re-hooks over the first run and corrupts it.
- The manager check became a `hasKsud()` helper, which tests `nativeLibraryDir` for null first.

### Fixed - unsafe getprop fallback
Upstream's new fallback for kernels that leave the Android version out of `uname -r` never checked the
`popen()` result and rebuilt the version string with `sprintf` into a 15 byte stack buffer from a 5 byte
read, so a long or non-numeric `ro.vendor.build.version.release` overflowed it. The property is now read
into a zero-initialised buffer and only a leading integer is accepted. The same hardening shipped in
[k10halifa/Dslice](https://github.com/k10halifa/Dslice).

### Built
- The eight embedded kernel modules are rebuilt by CI from this source and committed, so the APK carries
  exactly this engine.

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
