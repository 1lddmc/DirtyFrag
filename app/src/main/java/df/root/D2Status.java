package df.root;

import android.app.KeyguardManager;
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import java.util.Locale;

/**
 * Whether this firmware will let the device into Odin (download) mode, or answer
 * a download-mode request with Samsung's D2 error.
 *
 * Galaxy firmwares from around April 2026 refuse download mode when the device is
 * locked in the everyday sense of the word. Samsung's system_server runs a
 * com.android.server.DmcService that writes three booleans - the DMC vault - into
 * a vaultkeeper trustlet in the TEE, and the bootloader reads them back before
 * deciding whether to start Odin or print the D2 error. The vault is
 * [lock, maintenance, at_command]: lock follows the lock-screen credential,
 * maintenance follows Samsung's maintenance mode, at_command is set by the
 * AT+SUDDLMOD / AT+FUS service escape hatch and cleared by the next boot. The
 * bootloader refuses Odin on [1, 0, 0].
 *
 * The vault itself is unreadable from an app: it lives in a trustlet behind
 * android.permission.ACCESS_VAULTKEEPER_SERVICE, which is signature|privileged,
 * and DirtyFrag is deliberately not a root client - it is the tool that gets you
 * root, so it has to work on a device that has none. Reading the vault is what
 * the first attempt at this feature did, which is why it could only ever answer
 * "unsupported" on a real Galaxy.
 *
 * So instead of reading the vault this reads the two inputs the vault is built
 * from, both of them public API: the lock-screen credential through
 * KeyguardManager, and maintenance mode through the Settings provider. That is
 * the same pair of signals DmcService turns into [lock, maintenance], so the
 * answer is computed from the same facts rather than copied out of a vault we
 * cannot open.
 *
 * The one input that cannot be read is at_command. It is assumed to be 0, which
 * is what it is after every boot unless a service tool has just authorised the
 * device, and it fails in the safe direction: a wrong guess can only make the dot
 * red when Odin would in fact have worked, never green when it would not.
 */
final class D2Status {

    enum State {
        /** Not a Samsung device: there is nothing to indicate. */
        NOT_SAMSUNG,
        /** Firmware older than the lockdown, so Odin was never gated. */
        NOT_APPLICABLE,
        /** Odin will be let in. */
        OPEN,
        /** The D2 error: download mode refused while the device is locked. */
        BLOCKED
    }

    static final class Result {
        final State state;
        /** One line naming every input. Shown on long-press, and logged. */
        final String detail;

        Result(State state, String detail) {
            this.state = state;
            this.detail = detail;
        }
    }

    /**
     * Settings provider keys Samsung has been seen using for maintenance mode,
     * Global first then Secure. An absent key simply reads back null, so a wrong
     * guess costs a grey assumption rather than a crash - and the detail line
     * names the key that answered, so if Samsung renames it in some future
     * firmware a wrong dot is traceable from one log line instead of failing
     * silently the way the vault read did.
     */
    private static final String[] MAINTENANCE_KEYS = {
            "maintenance_mode",
            "maintenance_mode_status",
            "sec_maintenance_mode",
    };

    /**
     * First security patch level that carries the lockdown. Older firmware has no
     * DmcService and no vault, so a red dot there would be a lie and it gets a
     * grey one instead. The patch level is only a proxy for the firmware
     * generation, which is why it can decide grey-versus-red but never green.
     */
    private static final String FIRST_LOCKDOWN_PATCH = "2026-04-01";

    /**
     * Reads the state. Cheap, but call it off the UI thread anyway: probing the
     * Settings provider is a binder round trip per key.
     */
    static Result read(Context ctx) {
        if (!isSamsung()) {
            return new Result(State.NOT_SAMSUNG, Build.MANUFACTURER + " " + Build.MODEL
                    + ": the D2 lockdown is Samsung firmware, nothing to indicate");
        }

        boolean secure = isLockScreenSecured(ctx);
        String maintenanceKey = secure ? maintenanceModeKey(ctx) : null;
        boolean gated = secure && maintenanceKey == null;

        State state = predatesLockdown() ? State.NOT_APPLICABLE
                : gated ? State.BLOCKED : State.OPEN;

        StringBuilder d = new StringBuilder();
        d.append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(", patch ").append(Build.VERSION.SECURITY_PATCH)
                .append(": lockscreen=").append(secure ? "credential" : "none")
                .append(" maintenance=").append(maintenanceKey != null ? maintenanceKey : "off or unreadable")
                .append(" at_command=0 assumed -> ");
        switch (state) {
            case BLOCKED:
                d.append("D2 error expected, download mode refused");
                break;
            case NOT_APPLICABLE:
                d.append("firmware predates the lockdown, download mode never gated");
                break;
            default:
                d.append("Odin allowed");
                break;
        }
        return new Result(state, d.toString());
    }

    private static boolean isSamsung() {
        return contains(Build.MANUFACTURER, "samsung") || contains(Build.BRAND, "samsung");
    }

    /** Does the user have a PIN, pattern or password? That is the vault's lock. */
    private static boolean isLockScreenSecured(Context ctx) {
        KeyguardManager km = (KeyguardManager) ctx.getSystemService(Context.KEYGUARD_SERVICE);
        return km != null && km.isDeviceSecure();
    }

    /** The Settings key that says maintenance mode is on, or null if none does. */
    private static String maintenanceModeKey(Context ctx) {
        for (String key : MAINTENANCE_KEYS) {
            try {
                if (isOn(Settings.Global.getString(ctx.getContentResolver(), key))
                        || isOn(Settings.Secure.getString(ctx.getContentResolver(), key))) {
                    return key;
                }
            } catch (Exception e) {
                // A key we are not allowed to read is indistinguishable from one
                // that is not there, which the detail line already admits.
            }
        }
        return null;
    }

    private static boolean isOn(String value) {
        if (value == null) return false;
        String v = value.trim();
        return v.equals("1") || v.equalsIgnoreCase("true") || v.equalsIgnoreCase("on");
    }

    /** SECURITY_PATCH is a "yyyy-MM-dd" string, so it compares as written. */
    private static boolean predatesLockdown() {
        String patch = Build.VERSION.SECURITY_PATCH;
        if (patch == null || patch.length() < 10) return true;
        return patch.compareTo(FIRST_LOCKDOWN_PATCH) < 0;
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    private D2Status() {}
}
