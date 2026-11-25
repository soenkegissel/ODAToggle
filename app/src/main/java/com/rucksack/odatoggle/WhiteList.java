package com.rucksack.odatoggle;

import android.content.Context;
import android.content.Intent;

import com.fujitsu_ten.displayaudio.whitelist.common.AllowedInstallation;
import com.fujitsu_ten.displayaudio.whitelist.common.AllowedInstallationList;
import com.fujitsu_ten.displayaudio.whitelist.common.AppPriority;
import com.fujitsu_ten.displayaudio.whitelist.common.AppPriorityList;
import com.fujitsu_ten.displayaudio.whitelist.common.IWhiteList;
import com.fujitsu_ten.displayaudio.whitelist.common.ProcessControl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Liest und schreibt die Prozess-Whitelist des Honda-Radios ueber die System-API
 * com.fujitsu_ten.displayaudio.whitelist.common.IWhiteList (im Radio vorhanden).
 * Das Binaerformat entspricht dem, was S_Mikes Permissions-App an IWhiteList.update() uebergibt:
 *
 *   [0..1]  Gesamtlaenge (big endian)
 *   [2..3]  Laenge Process-Controls-Block
 *   [4..]   Process-Controls-Block
 *   [2 B]   Laenge Allowed-Installations-Block
 *   [..]    Allowed-Installations-Block
 *   [3 B]   Versionsnummer
 *   Puffergroesse insgesamt 0xFFFF Byte.
 */
final class WhiteList {

    private static final int BUFFER_SIZE = 0xFFFF;
    private static final int RESERVED_SIZE = 12;

    private int version;
    private AllowedInstallationList allowed;
    private final LinkedHashMap<AppPriority, ProcessControl> controls = new LinkedHashMap<>();

    private WhiteList() { }

    /** Liest die aktuelle Whitelist aus dem Radio. */
    static WhiteList load() {
        WhiteList wl = new WhiteList();

        byte[] info = new byte[8];
        IWhiteList.loadServiceInfo(info);
        wl.version = ((info[0] & 0xFF) << 16) | ((info[1] & 0xFF) << 8) | (info[2] & 0xFF);

        wl.allowed = IWhiteList.getAllowedInstallationList();
        if (wl.allowed == null || wl.allowed.allowedInstallations == null) {
            throw new ToggleException(R.string.error_allowed_list);
        }

        AppPriorityList priorities = IWhiteList.getAppPriorityList();
        if (priorities == null || priorities.appPriorities == null) {
            throw new ToggleException(R.string.error_process_list);
        }
        for (AppPriority p : priorities.appPriorities) {
            ProcessControl pc = IWhiteList.getProcessControl(p.processName, new String[]{p.packageName});
            wl.controls.put(p, pc);
        }
        return wl;
    }

    /** Sucht den Eintrag fuer einen Prozessnamen, z. B. "it.smg.hu". */
    ProcessControl find(String processName) {
        for (Map.Entry<AppPriority, ProcessControl> e : controls.entrySet()) {
            if (processName.equals(e.getKey().processName) && e.getValue() != null) {
                return e.getValue();
            }
        }
        return null;
    }

    /** Schreibt die Whitelist zurueck ins Radio und benachrichtigt das System. */
    void save(Context context) throws IOException {
        byte[] pcBlock = serializeProcessControls();
        byte[] aiBlock = serializeAllowedInstallations();

        byte[] buf = new byte[BUFFER_SIZE];
        int pos = 4;
        System.arraycopy(pcBlock, 0, buf, pos, pcBlock.length);
        buf[2] = (byte) ((pcBlock.length >> 8) & 0xFF);
        buf[3] = (byte) (pcBlock.length & 0xFF);
        pos += pcBlock.length;

        buf[pos] = (byte) ((aiBlock.length >> 8) & 0xFF);
        buf[pos + 1] = (byte) (aiBlock.length & 0xFF);
        pos += 2;
        System.arraycopy(aiBlock, 0, buf, pos, aiBlock.length);
        pos += aiBlock.length;

        buf[pos] = (byte) ((version >> 16) & 0xFF);
        buf[pos + 1] = (byte) ((version >> 8) & 0xFF);
        buf[pos + 2] = (byte) (version & 0xFF);
        pos += 3;

        buf[0] = (byte) ((pos >> 8) & 0xFF);
        buf[1] = (byte) (pos & 0xFF);

        int before = IWhiteList.getUpdateCount();
        IWhiteList.update(buf);
        int after = IWhiteList.getUpdateCount();
        if (after <= before) {
            throw new ToggleException(R.string.error_not_applied);
        }

        Intent intent = new Intent(
                com.fujitsu_ten.displayaudio.whitelist.common.Intent.ACTION_WHITE_LIST_UPDATE);
        intent.putExtra(
                com.fujitsu_ten.displayaudio.whitelist.common.Intent.EXTRA_WHITE_LIST_UPDATE_COUNT, after);
        context.sendBroadcast(intent);
    }

    private byte[] serializeProcessControls() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int n = controls.size();
        out.write((n >> 8) & 0xFF);
        out.write(n & 0xFF);
        for (Map.Entry<AppPriority, ProcessControl> e : controls.entrySet()) {
            AppPriority p = e.getKey();
            ProcessControl pc = e.getValue();
            if (pc == null) {
                throw new ToggleException(R.string.error_no_entry, p.processName);
            }
            writeString(out, p.processName);
            writeString(out, p.packageName);
            out.write(pc.authType);
            out.write(pc.appType);
            out.write(pc.videoOut);
            out.write(pc.soundOut);
            out.write(pc.soundInterrupt);
            out.write(pc.soundInterruptMute);
            out.write(pc.lastMode);
            out.write(pc.oomSetPerm);
            out.write(p.processKillTarget);
            out.write(reserved(pc));
        }
        return out.toByteArray();
    }

    private byte[] serializeAllowedInstallations() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        AllowedInstallation[] list = allowed.allowedInstallations;
        out.write((list.length >> 8) & 0xFF);
        out.write(list.length & 0xFF);
        for (AllowedInstallation a : list) {
            byte[] name = a.apkFileName.getBytes("US-ASCII");
            out.write(name.length);
            out.write(name);
            out.write(0);
            out.write(a.sigDigestSha256);
        }
        return out.toByteArray();
    }

    private static void writeString(ByteArrayOutputStream out, String s) throws IOException {
        byte[] b = s.getBytes("US-ASCII");
        out.write(b.length);
        out.write(b);
    }

    private static byte[] reserved(ProcessControl pc) {
        if (pc.reserved == null || pc.reserved.length != RESERVED_SIZE) {
            pc.reserved = new byte[RESERVED_SIZE];
        }
        return pc.reserved;
    }
}
