"""Offline request-gate tests; no actual requests, product or build."""
import importlib.util
import io
import json
from pathlib import Path
import sys
import unittest

spec=importlib.util.spec_from_file_location('closed',Path(__file__).with_name('descargar-lote-cerrado.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)

class Checks(unittest.TestCase):
    def setUp(self):
        m.stop_event.clear();self.plan=json.loads(m.PLAN.read_text())
        self.allowed,self.products=m.allowlist(self.plan);self.down=m.Downloader(self.allowed)
    def test_counts(self):self.assertEqual(len(self.plan['maven'])+len(self.plan['nuget']),220)
    def test_signature_scope(self):self.assertEqual(len(self.products),9)
    def test_known_jar(self):self.down.guard(self.plan['maven'][0]['url'])
    def test_known_nupkg(self):self.down.guard(self.plan['nuget'][0]['url'])
    def test_checksum(self):self.down.guard(self.plan['maven'][0]['url']+'.sha512')
    def test_product_signature(self):self.down.guard(next(iter(self.products))+'.asc')
    def test_other_jar(self):
        with self.assertRaises(m.Stop):self.down.guard(m.BASE+'unlisted.jar')
    def test_other_domain(self):
        with self.assertRaises(m.Stop):self.down.guard('https://example.invalid/file.jar')
    def test_other_type(self):
        with self.assertRaises(m.Stop):self.down.guard(self.plan['maven'][0]['url']+'.exe')
    def test_no_keyserver(self):
        with self.assertRaises(m.Stop):self.down.guard('https://keys.openpgp.org/')
    def test_no_nupkg_extra_sidecar(self):
        with self.assertRaises(m.Stop):self.down.guard(self.plan['nuget'][0]['url']+'.sha512')
    def test_count_change(self):
        self.plan['maven'].pop()
        with self.assertRaises(m.Stop):m.allowlist(self.plan)
    def test_query_not_listed(self):
        with self.assertRaises(m.Stop):self.down.guard(self.plan['maven'][0]['url']+'?extra=1')
    def test_stop_flag(self):
        with self.assertRaises(m.Stop):self.down.guard('https://example.invalid/')
        self.assertTrue(m.stop_event.is_set())

if __name__=='__main__':
    result=unittest.TextTestRunner(stream=io.StringIO()).run(unittest.defaultTestLoader.loadTestsFromTestCase(Checks))
    print('OFFLINE_TESTS total='+str(result.testsRun)+' failures='+str(len(result.failures))+' errors='+str(len(result.errors)))
    for case,_ in result.failures+result.errors:print('FAIL case='+case.id().split('.')[-1])
    sys.exit(0 if result.wasSuccessful() else 1)
