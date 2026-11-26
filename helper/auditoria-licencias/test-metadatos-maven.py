"""Offline fixtures for the bounded metadata reader; never executes Maven."""
import importlib.util
import io
from pathlib import Path
import sys
import unittest
import xml.etree.ElementTree as ET

spec=importlib.util.spec_from_file_location('reader',Path(__file__).with_name('cerrar-metadatos-maven.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)

def pom(body):return ET.fromstring('<project xmlns="http://maven.apache.org/POM/4.0.0">'+body+'</project>')
def dependency(a,v='',extra=''):
    return '<dependency><groupId>x</groupId><artifactId>'+a+'</artifactId>'+('<version>'+v+'</version>' if v else '')+extra+'</dependency>'

class Fixture(m.Reader):
    def __init__(self,documents):super().__init__();self.documents=documents
    def pom(self,gav):return self.documents[gav]

class Checks(unittest.TestCase):
    def test_pom_allowed(self):self.assertTrue(m.permitted(m.BASE+'x/a/1/a-1.pom'))
    def test_metadata_allowed(self):self.assertTrue(m.permitted(m.BASE+'x/a/maven-metadata.xml'))
    def test_checksum_allowed(self):self.assertTrue(m.permitted(m.BASE+'x/a/1/a-1.pom.sha512'))
    def test_jar_blocked(self):self.assertFalse(m.permitted(m.BASE+'x/a/1/a-1.jar'))
    def test_signature_blocked(self):self.assertFalse(m.permitted(m.BASE+'x/a/1/a-1.pom.asc'))
    def test_domain_blocked(self):self.assertFalse(m.permitted('https://example.invalid/a.pom'))
    def test_redirect_blocked(self):
        import urllib.request
        with self.assertRaises(m.Stop):m.RestrictedRedirects().redirect_request(urllib.request.Request(m.BASE+'a.pom'),None,302,'',{},'https://example.invalid/a.pom')
    def test_query_blocked(self):self.assertFalse(m.permitted(m.BASE+'a.pom?x=1'))
    def test_range_not_fixed(self):self.assertFalse(m.fixed('[1,2)'))
    def test_snapshot_not_fixed(self):self.assertFalse(m.fixed('1-SNAPSHOT'))
    def test_latest_not_fixed(self):self.assertFalse(m.fixed('LATEST'))
    def test_nested_property(self):self.assertEqual(m.expand('${a}',{'a':'${b}','b':'1'}),'1')
    def test_inherited_management_child_override(self):
        parent=pom('<properties><dep.version>1</dep.version></properties><dependencyManagement><dependencies>'+dependency('a','${dep.version}')+'</dependencies></dependencyManagement>')
        child=pom('<parent><groupId>x</groupId><artifactId>parent</artifactId><version>1</version></parent><properties><dep.version>2</dep.version></properties><dependencies>'+dependency('a')+'</dependencies>')
        r=Fixture({('x','parent','1'):parent,('x','child','1'):child})
        self.assertEqual(r.model(('x','child','1'))['dependencies'][0]['version'],'2')
    def test_bom_import(self):
        bom=pom('<dependencyManagement><dependencies>'+dependency('a','2')+'</dependencies></dependencyManagement>')
        child=pom('<dependencyManagement><dependencies>'+dependency('bom','1','<type>pom</type><scope>import</scope>')+'</dependencies></dependencyManagement><dependencies>'+dependency('a')+'</dependencies>')
        r=Fixture({('x','bom','1'):bom,('x','child','1'):child})
        self.assertEqual(r.model(('x','child','1'))['dependencies'][0]['version'],'2')
    def test_windows_profile(self):
        r=Fixture({});p=ET.fromstring('<profile xmlns="http://maven.apache.org/POM/4.0.0"><activation><os><family>windows</family><arch>amd64</arch></os></activation></profile>')
        self.assertTrue(r.active(p,('x','a','1')))
    def test_no_invented_javafx_override(self):
        r=Fixture({});p=ET.fromstring('<profile xmlns="http://maven.apache.org/POM/4.0.0"><activation><property><name>javafx.platform</name></property></activation></profile>')
        self.assertFalse(r.active(p,('x','a','1')))
    def test_test_optional_provided_excluded(self):
        docs={('x','a','1'):pom('<dependencies>'+dependency('test','1','<scope>test</scope>')+dependency('optional','1','<optional>true</optional>')+dependency('provided','1','<scope>provided</scope>')+'</dependencies>')}
        r=Fixture(docs);r.graph('fixture',[(('x','a','1'),'jar','')])
        self.assertEqual(len(r.binaries),1)
    def test_nearest_first(self):
        docs={('x','a','1'):pom('<dependencies>'+dependency('b','1')+'</dependencies>'),('x','b','2'):pom('')}
        r=Fixture(docs);r.graph('fixture',[(('x','a','1'),'jar',''),(('x','b','2'),'jar','')])
        self.assertIn((('x','b','2'),'jar',''),r.binaries)
        self.assertNotIn(('x','b','1'),r.models)
    def test_exclusion(self):
        docs={('x','a','1'):pom('<dependencies>'+dependency('b','1','<exclusions><exclusion><groupId>x</groupId><artifactId>c</artifactId></exclusion></exclusions>')+'</dependencies>'),('x','b','1'):pom('<dependencies>'+dependency('c','1')+'</dependencies>')}
        r=Fixture(docs);r.graph('fixture',[(('x','a','1'),'jar','')]);self.assertEqual(len(r.binaries),2)
    def test_scope_range_issue_without_fetch(self):
        docs={('x','a','1'):pom('<dependencies>'+dependency('b','[1,2)')+'</dependencies>')}
        r=Fixture(docs);r.graph('fixture',[(('x','a','1'),'jar','')]);self.assertEqual(r.issues[0]['kind'],'NON_FIXED_VERSION')

if __name__=='__main__':
    result=unittest.TextTestRunner(stream=io.StringIO()).run(unittest.defaultTestLoader.loadTestsFromTestCase(Checks))
    print('OFFLINE_TESTS total='+str(result.testsRun)+' failures='+str(len(result.failures))+' errors='+str(len(result.errors)))
    for case,_ in result.failures+result.errors:print('FAIL case='+case.id().split('.')[-1])
    sys.exit(0 if result.wasSuccessful() else 1)
