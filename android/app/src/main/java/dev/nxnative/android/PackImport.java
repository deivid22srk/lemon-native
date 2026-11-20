package dev.nxnative.android;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Import pack… for the Peanuts redesign's game sheet (dev.peanuts.ui.GameSheet), the same flow as the game page's Storage tab (PackUi.importRun) but callable
 * from another package with the title, the game and the picked .zip only: no MainActivity or Game object is needed.
 * <p>
 * The zip is unpacked into {@code packs/<title ID>.partial} and checked by {@link PackZip#stage} (its format, its title, finished code, a shader library), its
 * code must fit the game's build ({@link PackCheck#blocking}), and only then does {@link PackZip#swapIn} replace the app's pack. The title's existing list
 * entries are pointed at the imported pack and a previously picked pack folder is let go, exactly as the game page's Import pack… does. Nothing is changed when
 * the zip is refused.
 */
public final class PackImport {
    /** Called on the UI thread: packDir is the imported pack, error why nothing was imported (null on success) */
    public interface Done { void done(File packDir, String error); }

    /**
     * Optional progress callback, called on the UI thread between the import's already-existing stages: 0 staging the zip into a partial folder (PackZip.stage),
     * 1 checking it against the game (PackCheck.blocking), 2 installing it (PackZip.swapIn). The import itself cannot be cancelled or paused — the listener only
     * drives progress UI (GameSheet's import dialog); the result still arrives through {@link Done} whatever the listener does
     */
    public interface Stages { void onStage(int stage); }

    /** The stage ids {@link Stages} receives, in the order they fire */
    public static final int STAGE_STAGING = 0, STAGE_CHECKING = 1, STAGE_INSTALLING = 2;

    private static final Handler UI = new Handler(Looper.getMainLooper());
    private static final Set<String> busy = Collections.synchronizedSet(new HashSet<>());

    private PackImport() {}

    /** Whether this title's pack is being imported (a second pick while one runs is refused) */
    public static boolean busy(String titleId) {
        return titleId != null && busy.contains(titleId.toUpperCase(java.util.Locale.ROOT));
    }

    /**
     * Imports a pack .zip made by Export pack… for titleId into the app's pack folder, after checking it against the game. Runs off the UI thread; done is
     * called on it. The same without a progress listener (all existing call sites)
     */
    public static void importZip(Context c, String titleId, File game, File source, Done done) {
        importZip(c, titleId, game, source, done, null);
    }

    /** The same, with an optional stage listener fired between the stages (null: none); see {@link Stages} */
    public static void importZip(Context c, String titleId, File game, File source, Done done, Stages stages) {
        String id = titleId == null ? "" : titleId.toUpperCase(java.util.Locale.ROOT);
        if (!id.matches("[0-9A-F]{16}")) { post(done, null, "That game has no title ID to import a pack for."); return; }
        if (game == null || !game.exists()) { post(done, null, "The game's files are not where the library found them. Rescan, or pick the game folder again."); return; }
        if (source == null || !source.isFile()) { post(done, null, "The picked pack is not a file the app can read. Pick it from the phone's storage."); return; }
        if (!busy.add(id)) { post(done, null, "This game's pack is already being imported."); return; }
        if (PackUi.busy(id)) { busy.remove(id); post(done, null, "An export or import of this game's pack is still working."); return; }
        new Thread(() -> {
            File target = AppStorage.pack(c, id);
            try {
                File packs = AppStorage.packs(c);
                long need = source.length();
                if (packs.getParentFile() != null && packs.getParentFile().getUsableSpace() < need + (256L << 20))
                    throw new IOException("the pack needs " + SaveStore.size(need) + ", and the app's storage has "
                            + SaveStore.size(packs.getParentFile().getUsableSpace()) + " free");
                File remembered = Job.remembered(c);
                if (remembered != null && remembered.isFile()) {
                    Job j = Job.read(remembered);
                    if (SaveStore.same(j.pack(), target) && !"done".equals(j.state()))
                        throw new IOException("a Prepare job of this game writes to " + target + " (" + j.statusText(false)
                                + "): let it finish, or forget it in its ⋯ Manage, first");
                }
                fire(stages, STAGE_STAGING);
                Pack p = PackZip.stage(source, packs, id);
                try {
                    fire(stages, STAGE_CHECKING);
                    String bad = PackCheck.blocking(c, p, game);
                    if (bad != null) throw new IOException(bad);
                    fire(stages, STAGE_INSTALLING);
                    PackZip.swapIn(p.dir, target);
                } finally {
                    if (p.dir.exists()) SaveStore.deleteTree(p.dir);
                }
                // The imported pack wins over a pack folder picked before (the same two lines as PackUi.importRun)
                String old = Folders.pickedPack(c, id);
                Folders.pickedPack(c, id, null);
                if (old != null && old.equals(Prefs.folder(c, "pack"))) Prefs.folder(c, "pack", null);
                pointEntriesAt(c, id, target);
                post(done, target, null);
            } catch (IOException | RuntimeException e) {
                String msg = e.getMessage();
                post(done, null, msg == null || msg.isEmpty() ? e.toString() : msg);
            } finally {
                busy.remove(id);
            }
        }, "peanuts-pack-import").start();
    }

    /** Existing list entries of the title use the imported pack from now on (Game.load/Game.save; MainActivity.refresh runs at the next fill) */
    private static void pointEntriesAt(Context c, String titleId, File pack) {
        List<Game> list = Game.load(c);
        boolean changed = false;
        for (int i = 0; i < list.size(); i++) {
            Game x = list.get(i);
            if (!x.titleId.equalsIgnoreCase(titleId) || x.packDir.equals(pack.getPath())) continue;
            list.set(i, new Game(x.titleId, x.name, x.gameDir, pack.getPath(), x.used));
            changed = true;
        }
        if (changed) Game.save(c, list);
    }

    private static void post(Done done, File pack, String error) {
        UI.post(() -> done.done(pack, error));
    }

    /** A stage moved: said on the UI thread, so the listener never sees a background thread */
    private static void fire(Stages stages, int stage) {
        if (stages != null) UI.post(() -> stages.onStage(stage));
    }
}
