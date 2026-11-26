"""Build candidate native dev/prod; external audit injection only in test targets."""
import shutil
from candidata_contexto import *
cmake=Path(r'C:\Program Files\Microsoft Visual Studio\18\Community\Common7\IDE\CommonExtensions\Microsoft\CMake\CMake\bin\cmake.exe')
if not cmake.is_file():raise RuntimeError('VERIFIED_INSTALLED_CMAKE_MISSING')
for profile in ('dev','prod'):
    directory=ROOT/'native'/profile
    args=[cmake,'-S',SOURCE/'native','-B',directory,'-G','Visual Studio 18 2026','-A','x64','-DBUILD_TESTING=ON','-DMACE_LHM_DEVELOPMENT='+('ON' if profile=='dev' else 'OFF'),'-DCMAKE_PROJECT_INCLUDE='+str(ROOT/'test-audit/audit.cmake')]
    if profile=='prod':args+=['-DMACE_LHM_RUNTIME_MANIFEST='+str(ROOT/'helper-publish-true/helper-runtime.manifest')]
    run('native-configure-audit-'+profile,args)
    run('native-build-'+profile,[cmake,'--build',directory,'--config','Release','--parallel','4','--','/nr:false'])
save('native-build-result.json',{'dev':0,'prod':0,'product_dll_instrumented':False,'external_test_instrumentation':True})
