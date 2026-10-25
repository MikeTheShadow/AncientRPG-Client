"""Check that public delivery never includes neighboring private or account files."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import zipfile

spec = importlib.util.spec_from_file_location('package_client', Path(__file__).with_name('package_client.py'))
packager = importlib.util.module_from_spec(spec)
spec.loader.exec_module(packager)


class PublicPackTest(unittest.TestCase):
    def test_archive_whitelists_mods_and_pins_prism_without_machine_state(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            mods = root / 'client-mod/build/demo-mods'
            mods.mkdir(parents=True)
            (root / 'client-mod/build.gradle.kts').write_text('"com.mojang:minecraft:26.2"\n"net.fabricmc:fabric-loader:0.19.3"\n"net.fabricmc.fabric-api:fabric-api:0.161.0+26.2"')
            (root / 'gradle.properties').write_text('ancientrpgRelease=0.5.0-dev\n')
            for name in ('client-mod-0.5.0-dev.jar', 'fabric-api-0.161.0+26.2.jar'):
                with zipfile.ZipFile(mods / name, 'w') as jar:
                    jar.writestr('fabric.mod.json', json.dumps({'version': '0.5.0-dev'}))
            for name in ('LICENSE', 'THIRD_PARTY_NOTICES.md', 'docs/tester-quickstart.md', 'accounts.json', 'server/private.txt'):
                target = root / name
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text('test fixture')
            output = root / 'output.zip'
            with patch.object(packager.subprocess, 'check_output', return_value='a' * 40):
                packager.package(root, output)
            with zipfile.ZipFile(output) as archive:
                self.assertEqual(set(archive.namelist()), {
                    '.minecraft/mods/client-mod-0.5.0-dev.jar',
                    '.minecraft/mods/fabric-api-0.161.0+26.2.jar', 'mmc-pack.json',
                    'instance.cfg', 'environment.json', 'README.md', 'LICENSE', 'THIRD_PARTY_NOTICES.md'})
                self.assertEqual(json.loads(archive.read('mmc-pack.json'))['components'], [
                    {'uid': 'net.minecraft', 'version': '26.2'},
                    {'uid': 'net.fabricmc.fabric-loader', 'version': '0.19.3'}])
                self.assertNotIn('JavaPath', archive.read('instance.cfg').decode())
            self.assertTrue(output.with_suffix('.zip.sha256').is_file())


if __name__ == '__main__':
    unittest.main()
