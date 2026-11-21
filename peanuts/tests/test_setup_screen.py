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
        self.assertIn('LibrarySettings.rowsChangedHook = null', page, 'the hook does not outlive the screen')

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


if __name__ == '__main__':
    unittest.main()
