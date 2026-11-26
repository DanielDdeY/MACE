"""Process journal: PID + Windows creation time; never terminate any process."""
import ctypes
from ctypes import wintypes
import os
from pathlib import Path
import time

kernel=ctypes.WinDLL('kernel32',use_last_error=True)
kernel.GetProcessTimes.argtypes=[wintypes.HANDLE]+[ctypes.POINTER(wintypes.FILETIME)]*4
kernel.GetProcessTimes.restype=wintypes.BOOL
kernel.OpenProcess.argtypes=[wintypes.DWORD,wintypes.BOOL,wintypes.DWORD]
kernel.OpenProcess.restype=wintypes.HANDLE
kernel.CloseHandle.argtypes=[wintypes.HANDLE]
def birth(handle):
    values=[wintypes.FILETIME() for _ in range(4)]
    if not kernel.GetProcessTimes(handle,*[ctypes.byref(v) for v in values]):raise RuntimeError('PROCESS_CREATION_TIME_UNAVAILABLE')
    return ((values[0].dwHighDateTime<<32)|values[0].dwLowDateTime)//10000-11644473600000
def record(event,pid,created,parent,requester=None,directory=None):
    directory=Path(directory or os.environ['TEMP'])
    if not (directory/'.mace-candidate-process-audit').is_file():raise RuntimeError('AUDIT_MARKER_MISSING')
    with (directory/f'p_{pid}_{created}.audit').open('a',encoding='ascii') as file:
        file.write(f'{event} {pid} {created} {parent} {requester or os.getpid()}\n')
def register(child):
    record('CREATE',child.pid,birth(int(child._handle)),os.getpid())
def self_record():
    kernel.GetCurrentProcess.restype=wintypes.HANDLE
    record('SELF',os.getpid(),birth(kernel.GetCurrentProcess()),os.getppid())
def read(directory):
    rows=[]
    for file in Path(directory).glob('p_*.audit'):
        for line in file.read_text(encoding='ascii').splitlines():
            parts=line.split()
            if len(parts)!=5:raise RuntimeError('INVALID_AUDIT_RECORD')
            rows.append(dict(event=parts[0],pid=int(parts[1]),birth_ms=int(parts[2]),parent=int(parts[3]),requester=int(parts[4])))
    return rows
def survivors(rows):
    identities={(row['pid'],row['birth_ms']) for row in rows}
    alive=[]
    for pid,created in sorted(identities):
        handle=kernel.OpenProcess(0x1000,False,pid)
        if not handle:
            if ctypes.get_last_error() not in (87,):raise RuntimeError('SURVIVOR_QUERY_DENIED')
            continue
        try:
            if birth(handle)==created:
                code=wintypes.DWORD();kernel.GetExitCodeProcess.argtypes=[wintypes.HANDLE,ctypes.POINTER(wintypes.DWORD)]
                if not kernel.GetExitCodeProcess(handle,ctypes.byref(code)):raise RuntimeError('SURVIVOR_STATE_UNAVAILABLE')
                if code.value==259:alive.append({'pid':pid,'birth_ms':created})
        finally:kernel.CloseHandle(handle)
    return alive
def finish(directory):
    rows=read(directory)
    for _ in range(30):
        live=survivors(rows)
        if not live:return rows,[]
        time.sleep(.1)
    return rows,live
