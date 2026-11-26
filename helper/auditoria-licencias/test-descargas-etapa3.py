"""Offline checks of the Stage 3 download gate; no product/network execution."""
import hashlib
import importlib.util
import io
from pathlib import Path
import tempfile
import unittest
import sys
from unittest.mock import patch
import urllib.request
import zipfile

spec = importlib.util.spec_from_file_location('stage3', Path(__file__).with_name('preparar-descargas-etapa3.py'))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class DownloadGateTests(unittest.TestCase):
    def test_parent_namespace(self):
        pom='<project xmlns="http://maven.apache.org/POM/4.0.0"><parent><groupId>org.apache.maven.plugins</groupId><artifactId>maven-plugins</artifactId><version>43</version></parent></project>'
        self.assertEqual(module.parent_from_pom(pom),('org.apache.maven.plugins','maven-plugins','43'))

    def test_parent_absent(self):
        self.assertIsNone(module.parent_from_pom('<project/>'))

    def test_unknown_parent_not_authorized(self):
        self.assertNotIn(('org.apache.maven.plugins','maven-plugins','43'),module.APPROVED_GAV)

    def test_plugin_authorized(self):
        self.assertIn(('org.apache.maven.plugins','maven-dependency-plugin','3.8.1'),module.APPROVED_GAV)

    def test_unapproved_version(self):
        self.assertNotIn(('org.mockito','mockito-core','999'),module.APPROVED_GAV)

    def test_wrong_origin(self):
        with self.assertRaisesRegex(module.GateError,'ORIGIN_NOT_APPROVED'):
            module.fetch('https://example.invalid/x',Path('unused'))

    def test_insecure_origin(self):
        with self.assertRaisesRegex(module.GateError,'ORIGIN_NOT_APPROVED'):
            module.fetch('http://repo.maven.apache.org/x',Path('unused'))

    def test_url_credentials(self):
        with self.assertRaisesRegex(module.GateError,'ORIGIN_NOT_APPROVED'):
            module.fetch('https://fixture:fixture'+'@'+'repo.maven.apache.org/x',Path('unused'))

    def test_wrong_redirect(self):
        with self.assertRaisesRegex(module.GateError,'REDIRECT_NOT_APPROVED'):
            module.OfficialRedirects().redirect_request(urllib.request.Request(module.JDK_URL),None,302,'',{},'https://example.invalid/x')

    def test_checksum_match(self):
        with tempfile.TemporaryDirectory() as temp, patch.object(module,'ROOT',Path(temp)):
            data=b'approved fixture'
            path=Path(temp)/'file'
            path.write_bytes(data)
            path.with_name('file.sha256').write_text(hashlib.sha256(data).hexdigest())
            result=module.verified(module.JDK_URL,module.JDK_URL+'.sha256.txt','file','sha256')
            self.assertTrue(result['verified'])
            self.assertEqual(result['signature'],'NOT_VERIFIED')

    def test_checksum_mismatch(self):
        with tempfile.TemporaryDirectory() as temp, patch.object(module,'ROOT',Path(temp)):
            path=Path(temp)/'file'
            path.write_bytes(b'changed')
            path.with_name('file.sha256').write_text(hashlib.sha256(b'original').hexdigest())
            with self.assertRaisesRegex(module.GateError,'EXISTING_CHECKSUM_MISMATCH'):
                module.verified(module.JDK_URL,module.JDK_URL+'.sha256.txt','file','sha256')

    def test_zip_escape(self):
        with tempfile.TemporaryDirectory() as temp, patch.object(module,'ROOT',Path(temp)):
            with zipfile.ZipFile(Path(temp)/'fixture.zip','w') as archive:
                archive.writestr('../outside','bad')
            with self.assertRaisesRegex(module.GateError,'ZIP_PATH_REJECTED'):
                module.extract({'file':'fixture.zip'},'tool')
            self.assertFalse((Path(temp)/'outside').exists())

if __name__ == '__main__':
    suite=unittest.defaultTestLoader.loadTestsFromTestCase(DownloadGateTests)
    result=unittest.TextTestRunner(stream=io.StringIO()).run(suite)
    print('OFFLINE_TESTS total='+str(result.testsRun)+' failures='+str(len(result.failures))+' errors='+str(len(result.errors)))
    for case,_ in result.failures+result.errors:
        print('FAIL case='+case.id().split('.')[-1])
    sys.exit(0 if result.wasSuccessful() else 1)
