"""The first-run setup screen (dev.peanuts.ui.SetupActivity): the wiring — MainActivity launches it once
(saved == null) and refreshes Home on its result before the library's pickers read the same call, the
manifest registers it with Theme.Banana and no uiMode suppression (the Appearance choice remakes it), the
gate lets an intent that opens content take over, the pickers ride on LibrarySettings with the wizard's
rowsChangedHook, and every text of the screen is a resource in en and pt-BR (the Lemon two-locale rule)."""
import re
import unittest
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
UI = REPO / 'android/app/src/main/java/dev/peanuts/ui'
MAIN = REPO / 'android/app/src/main/java/dev/nxnative/android/MainActivity.java'
MANIFEST = REPO / 'android/app/src/main/AndroidManifest.xml'
RES = REPO / 'android/app/src/main/res'


class Wiring(unittest.TestCase):
    def test_hooks(self):
        main = MAIN.read_text(encoding='utf-8')
        hooks = [l for l in main.splitlines() if 'SetupActivity.launch(' in l]
        self.assertEqual(len(hooks), 1, 'the launch is one hook, once')
        self.assertIn('LEMON', hooks[0], 'an upstream hunk carries its marker')
        self.assertIn('saved == null', hooks[0], 'an activity made again does not relaunch the setup')
        results = [l for l in main.splitlines() if 'SetupActivity.REQ_SETUP' in l]
        self.assertEqual(len(results), 1, 'the result is handled once')
        self.assertIn('refresh()', results[0], 'Home is drawn again when the setup finishes')
        self.assertIn('LEMON', results[0])
        self.assertLess(main.index('SetupActivity.REQ_SETUP'), main.index('LibrarySettings.result(this'),
                        'the result is read before the library pickers consume the call')

    def test_manifest(self):
        line = re.search(r'<activity android:name="dev\.peanuts\.ui\.SetupActivity"[^\n]*', MANIFEST.read_text(encoding='utf-8'))
        self.assertIsNotNone(line, 'the screen is registered')
        element = line.group(0).split('>', 1)[0]   # the element without its trailing comment
        for attr in ('android:exported="false"', '@style/Theme.Banana'):
            self.assertIn(attr, element)
        self.assertNotIn('uiMode', element, 'the Appearance choice remakes the screen')
        self.assertIn('LEMON', line.group(0), 'the manifest hunk carries its marker')

    def test_gate_and_pickers(self):
        page = (UI / 'SetupActivity.java').read_text(encoding='utf-8')
        self.assertIn('PeanutsPrefs.setupDone(a)', page, 'done once, done for good')
        for excused in ('getData() != null', 'hasExtra("game")', 'hasExtra("add_game")', 'hasExtra("frontend_game")', 'hasExtra("ondevice_dump")'):
            self.assertIn(excused, page, 'an intent that opens content takes over the first run')
        self.assertIn('LibrarySettings.result(this', page, 'the pickers ride on the same door Settings uses')
        self.assertIn('rowsChangedHook', page, 'a firmware read lands: the wizard is drawn again')
        self.assertIn('protected void onStart()', page, 'the hook is registered while the screen is in front')
        self.assertIn('protected void onStop()', page, 'and cleared while it is covered (nothing redraws a finishing wizard)')
        self.assertIn('current.post(', page, 'the pills\' auto-scroll waits for the chip\'s layout (a recreated activity lands scrolled)')
        self.assertIn('LibrarySettings.resume(this)', page, 'back from Android\'s access page, a waiting firmware is read (the round-2 regression)')

    def test_no_legacy_value_lines(self):
        page = (UI / 'SetupActivity.java').read_text(encoding='utf-8')
        self.assertNotIn('keysValue(', page, 'the wizard speaks its own resources, not the Settings rows\' English values')
        self.assertNotIn('fontsValue(', page)
        self.assertNotIn('fontsState', page)

    def test_background_runs(self):
        page = (UI / 'SetupActivity.java').read_text(encoding='utf-8')
        self.assertIn('bananas.setRunning(true)', page, 'the lemons drift while the screen is in front')
        self.assertIn('bananas.setRunning(false)', page, 'and stop while it is covered, as Home\'s do')


class TwoLocale(unittest.TestCase):
    def test_every_used_string_exists_in_both_locales(self):
        page = (UI / 'SetupActivity.java').read_text(encoding='utf-8')
        en = (RES / 'values/strings.xml').read_text(encoding='utf-8')
        pt = (RES / 'values-pt-rBR/strings.xml').read_text(encoding='utf-8')
        used = set(re.findall(r'R\.string\.(lemon_\w+)', page))
        self.assertTrue(used, 'the screen speaks through resources')
        for name in sorted(used):
            self.assertIn(f'name="{name}"', en, f'{name} missing in values/strings.xml (en)')
            self.assertIn(f'name="{name}"', pt, f'{name} missing in values-pt-rBR/strings.xml (pt-BR)')

    def test_setup_blocks_are_paired(self):
        en = set(re.findall(r'name="(lemon_setup_\w+)"', (RES / 'values/strings.xml').read_text(encoding='utf-8')))
        pt = set(re.findall(r'name="(lemon_setup_\w+)"', (RES / 'values-pt-rBR/strings.xml').read_text(encoding='utf-8')))
        self.assertEqual(en, pt, f'en/pt-BR drift: {sorted(en ^ pt)}')

    def test_no_dead_library_strings(self):
        # every library_* resource declared is referenced somewhere in the app's Java (a dead resource lies about the i18n coverage)
        java = '\n'.join(p.read_text(encoding='utf-8') for p in (REPO / 'android/app/src/main/java').rglob('*.java'))
        for res in ((RES / 'values/strings.xml'), (RES / 'values-pt-rBR/strings.xml')):
            names = set(re.findall(r'name="(library_\w+)"', res.read_text(encoding='utf-8')))
            for name in sorted(names):
                self.assertIn(f'R.string.{name}', java, f'{name} is declared but never used')

    def test_firmware_states_through_one_door(self):
        # every firmware-read end state goes through fontsStop (kind and detail together), so no path can move one without the other
        ls = (UI / 'LibrarySettings.java').read_text(encoding='utf-8')
        self.assertIn('private static void fontsStop(int kind, String state) { fontsKind = kind; fontsState = state; }', ls, 'one door, defined once')
        self.assertNotIn('fontsState = "Firmware not added', ls, 'failure details ride on fontsStop, not on naked writes')
        self.assertIn('fontsStop(FONTS_FAILED, "Firmware not added: add your prod.keys first', ls, 'the keys-missing failure names itself')
        self.assertIn('fontsStop(r == 0 ? FONTS_IDLE : FONTS_FAILED', ls, 'the importer verdict names itself, success and failure')
        self.assertIn('fontsStop(FONTS_FAILED, "Firmware not added: could not copy the zip', ls, 'the zip-copy failure names itself')
        self.assertIn('fontsStop(FONTS_NO_IMPORTER', ls, 'a build without the importer is its own state, not a generic failure')
        self.assertIn('fontsStop(FONTS_WAITING', ls, 'waiting for the access is its own state')
        self.assertIn('fontsStop(FONTS_IDLE', ls, 'a landed read clears the state')

    def test_library_dialogs_paired(self):
        # the wizard opens LibrarySettings' dialogs: their texts are resources in both locales too (LibrarySettings itself is touched by the wizard's hook)
        ls = (UI / 'LibrarySettings.java').read_text(encoding='utf-8')
        used = set(re.findall(r'R\.string\.(library_\w+)', ls))
        self.assertTrue(used, 'the wizard-reachable dialogs speak resources')
        en = (RES / 'values/strings.xml').read_text(encoding='utf-8')
        pt = (RES / 'values-pt-rBR/strings.xml').read_text(encoding='utf-8')
        for name in sorted(used):
            self.assertIn(f'name="{name}"', en, f'{name} missing in values/strings.xml (en)')
            self.assertIn(f'name="{name}"', pt, f'{name} missing in values-pt-rBR/strings.xml (pt-BR)')


if __name__ == '__main__':
    unittest.main()
