// Manual QA only. Not loaded into MACE and never copied to the image.
using System;
using System.IO;
using System.Text;
using System.Diagnostics;
using System.Collections.Generic;
using System.Runtime.InteropServices;
using System.Text.RegularExpressions;
using System.Threading;

namespace Mace.PackagingTests {
    public sealed class ManualActor {
        public Process Launcher, Jvm;
        public int RootId;
        public long RootBirthTicks;
        public string Executable, JvmLibrary, NativeLibrary, Helper, Blackbox;
        public bool Simulated, SawJvm, SawHelper;
        public bool HelperQueryOk;
        public readonly Dictionary<int,Process> Helpers = new Dictionary<int,Process>();
        public readonly Dictionary<string,int> GpuLabels = new Dictionary<string,int>();
        public ulong PreviousTick;
    }
    public sealed class ManualSampler : IDisposable {
        readonly string log; readonly ManualResetEvent stop=new ManualResetEvent(false);
        readonly Thread worker; ManualActor actor;
        public ManualSampler(string path,ManualActor initial) {
            log=path;actor=initial;
            worker=new Thread(Run);worker.IsBackground=true;worker.Start();
        }
        public void SetActor(ManualActor value) { Interlocked.Exchange(ref actor,value); }
        void Run() {
            while(!stop.WaitOne(0)) {
                Stopwatch elapsed=Stopwatch.StartNew();
                try { Manual52.Sample(log,actor,"PAWNIO_MONITOR"); }
                catch { try { Manual52.Log(log,"kind=NO_VERIFICADO reason=SAMPLER_UNAVAILABLE"); } catch { } }
                if(stop.WaitOne((int)Math.Max(0,1000-elapsed.ElapsedMilliseconds))) return;
            }
        }
        public bool JoinTimedOut;
        public void Dispose() { stop.Set(); JoinTimedOut=!worker.Join(3000); }
    }
    public static class Manual52 {
        static readonly object LogGate=new object();
        [DllImport("kernel32.dll")] public static extern ulong GetTickCount64();
        [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr window, out uint process);
        [StructLayout(LayoutKind.Sequential, CharSet=CharSet.Unicode)] struct Entry {
            public uint size, usage, pid; public UIntPtr heap; public uint module, threads, parent;
            public int priority; public uint flags;
            [MarshalAs(UnmanagedType.ByValTStr,SizeConst=260)] public string name;
        }
        [DllImport("kernel32.dll",SetLastError=true)] static extern IntPtr CreateToolhelp32Snapshot(uint flags,uint id);
        [DllImport("kernel32.dll",CharSet=CharSet.Unicode,ExactSpelling=true)] static extern bool Process32FirstW(IntPtr snapshot,ref Entry entry);
        [DllImport("kernel32.dll",CharSet=CharSet.Unicode,ExactSpelling=true)] static extern bool Process32NextW(IntPtr snapshot,ref Entry entry);
        [DllImport("kernel32.dll")] static extern bool CloseHandle(IntPtr handle);
        [DllImport("advapi32.dll",CharSet=CharSet.Unicode,SetLastError=true,ExactSpelling=true)] static extern IntPtr OpenSCManagerW(string machine,string database,uint access);
        [DllImport("advapi32.dll",CharSet=CharSet.Unicode,SetLastError=true,ExactSpelling=true)] static extern IntPtr OpenServiceW(IntPtr scm,string service,uint access);
        [DllImport("advapi32.dll")] static extern bool CloseServiceHandle(IntPtr handle);
        [StructLayout(LayoutKind.Sequential)] struct Status { public uint type,state,accepted,win32,specific,checkpoint,hint; }
        [StructLayout(LayoutKind.Sequential)] struct StatusEx { public uint type,state,accepted,win32,specific,checkpoint,hint,pid,flags; }
        [DllImport("advapi32.dll",SetLastError=true)] static extern bool QueryServiceStatusEx(IntPtr service,int level,out StatusEx status,int bytes,out int needed);
        [DllImport("advapi32.dll",SetLastError=true)] static extern bool ControlService(IntPtr service,uint control,out Status status);
        [DllImport("advapi32.dll",CharSet=CharSet.Unicode,SetLastError=true,ExactSpelling=true)] static extern bool StartServiceW(IntPtr service,int argc,IntPtr argv);
        public static bool Alive(Process process) { try { if (process==null) return false; process.Refresh(); return !process.HasExited; } catch { return false; } }
        public static bool Same(string a,string b) {
            if (String.IsNullOrEmpty(a)||String.IsNullOrEmpty(b)) return false;
            if(a.StartsWith(@"\\?\")) a=a.Substring(4);
            if(b.StartsWith(@"\\?\")) b=b.Substring(4);
            return String.Equals(Path.GetFullPath(a),Path.GetFullPath(b),StringComparison.OrdinalIgnoreCase);
        }
        public static void Log(string path,string fields) {
            // Fail closed if a caller accidentally supplies raw diagnostic/identity text.
            if (Regex.IsMatch(fields,@"[A-Za-z]:[\\/]|\\\\|S-\d-\d+(?:-\d+)+|(?i)(?<![0-9a-f])[0-9a-f]{64}(?![0-9a-f])") || fields.IndexOf('\n')>=0 || fields.IndexOf('\r')>=0)
                throw new InvalidOperationException("UNSAFE_LOG_FIELDS");
            lock(LogGate) File.AppendAllText(path,"utc="+DateTime.UtcNow.ToString("O")+" "+fields+Environment.NewLine,new UTF8Encoding(false));
        }
        static void BestLog(string path,string fields) { try { Log(path,fields); } catch { } }
        public static void BindJvm(ManualActor actor,Process process) {
            DateTime wanted=process.StartTime.ToUniversalTime(); IntPtr handle=process.Handle;
            process.Refresh(); if(process.HasExited||process.StartTime.ToUniversalTime()!=wanted) throw new InvalidOperationException("JVM_IDENTITY_CHANGED");
            lock(actor) {actor.Jvm=process;actor.SawJvm=true;}
        }
        // Returns Win32 error, never installer/force operations. State 0 = unavailable.
        static int Service(int operation,out uint state) {
            state=0; IntPtr scm=OpenSCManagerW(null,null,1);
            if(scm==IntPtr.Zero) return Marshal.GetLastWin32Error();
            try {
                uint access=4u+(operation==1?32u:operation==2?16u:0u);
                IntPtr service=OpenServiceW(scm,"PawnIO",access);
                if(service==IntPtr.Zero) return Marshal.GetLastWin32Error();
                try {
                    if(operation==1) { Status response; if(!ControlService(service,1,out response)) return Marshal.GetLastWin32Error(); }
                    if(operation==2 && !StartServiceW(service,0,IntPtr.Zero)) { int error=Marshal.GetLastWin32Error(); if(error!=1056) return error; }
                    StatusEx status; int needed;
                    if(!QueryServiceStatusEx(service,0,out status,Marshal.SizeOf(typeof(StatusEx)),out needed)) return Marshal.GetLastWin32Error();
                    state=status.state; return 0;
                } finally { CloseServiceHandle(service); }
            } finally { CloseServiceHandle(scm); }
        }
        public static int PawnState(out uint state) { return Service(0,out state); }
        public static int StopPawnNormally(out uint state) { return Service(1,out state); }
        public static int StartPawn(out uint state) { return Service(2,out state); }
        public static string StateName(uint state) {
            switch(state) {case 1:return "STOPPED";case 2:return "START_PENDING";case 3:return "STOP_PENDING";case 4:return "RUNNING";default:return "UNKNOWN";}
        }
        public static void RefreshHelpers(ManualActor actor) {
            lock(actor) {
            actor.HelperQueryOk=false;
            if(!Alive(actor.Jvm)) return;
            IntPtr snapshot=CreateToolhelp32Snapshot(2,0);
            if(snapshot==new IntPtr(-1)) return;
            try {
                bool uncertain=false;
                Entry entry=new Entry(); entry.size=(uint)Marshal.SizeOf(typeof(Entry));
                if(!Process32FirstW(snapshot,ref entry)) return;
                do {
                    if(entry.parent!=(uint)actor.Jvm.Id || !String.Equals(entry.name,Path.GetFileName(actor.Helper),StringComparison.OrdinalIgnoreCase)) continue;
                    try {
                        Process child=Process.GetProcessById((int)entry.pid);
                        if(child.StartTime.ToUniversalTime()<actor.Jvm.StartTime.ToUniversalTime().AddMilliseconds(-1)) {uncertain=true;continue;}
                        if(!Same(child.MainModule.FileName,actor.Helper)) {uncertain=true;continue;}
                        DateTime wanted=child.StartTime.ToUniversalTime(); IntPtr handle=child.Handle;
                        child.Refresh(); if(child.HasExited||child.StartTime.ToUniversalTime()!=wanted) {uncertain=true;continue;}
                        Process previous;
                        if(!actor.Helpers.TryGetValue(child.Id,out previous) || !Alive(previous)) actor.Helpers[child.Id]=child;
                        actor.SawHelper=true;
                    } catch { uncertain=true; /* Never adopt by global name. */ }
                } while(Process32NextW(snapshot,ref entry));
                actor.HelperQueryOk=!uncertain;
            } finally { CloseHandle(snapshot); }
            }
        }
        public static Process CurrentHelper(ManualActor actor) {
            lock(actor) {
            RefreshHelpers(actor);
            if(!actor.HelperQueryOk) return null;
            Process selected=null;
            foreach(Process helper in actor.Helpers.Values) if(Alive(helper)) {
                if(selected!=null) return null; // Ambiguous, do not choose a target.
                selected=helper;
            }
            return selected;
            }
        }
        static string HelperIds(ManualActor actor) {
            List<string> ids=new List<string>(); foreach(Process p in actor.Helpers.Values) if(Alive(p)) ids.Add(p.Id.ToString());
            return ids.Count==0?"none":String.Join(",",ids.ToArray());
        }
        static int ImageHelperCount(string expected) {
            int count=0;bool unknown=false;
            // Count-only diagnostic; never used to select a JVM or a termination target.
            foreach(Process process in Process.GetProcessesByName(Path.GetFileNameWithoutExtension(expected))) {
                try { if(Same(process.MainModule.FileName,expected)) count++; }
                catch { unknown=true; }
            }
            return unknown?-1:count;
        }
        static uint Crc(byte[] bytes,int length) {
            uint crc=0xffffffff;
            for(int i=0;i<length;i++) { crc^=bytes[i]; for(int b=0;b<8;b++) crc=(crc>>1)^((crc&1)!=0?0xedb88320u:0u); }
            return ~crc;
        }
        sealed class V2 {
            public string Status="MISSING", Cpu="cpu_t=-1 cpu_p=-1 cpu_u=-1", Gpu="";
            public long LatestWall, CpuWall, CpuStamp;
            public readonly Dictionary<string,string> Gpus=new Dictionary<string,string>();
            public readonly Dictionary<string,long> GpuWall=new Dictionary<string,long>();
        }
        static V2 ReadV2(ManualActor actor) {
            V2 result=new V2();
            if(actor.Jvm==null || String.IsNullOrEmpty(actor.Blackbox)||!Directory.Exists(actor.Blackbox)) return result;
            string best=null; long bestCreated=-1;
            foreach(string path in Directory.GetFiles(actor.Blackbox,"mace-blackbox-*.mbbx")) {
                try { using(FileStream stream=new FileStream(path,FileMode.Open,FileAccess.Read,FileShare.ReadWrite|FileShare.Delete)) {
                    byte[] h=new byte[64]; if(stream.Read(h,0,64)!=64) continue;
                    if(Encoding.ASCII.GetString(h,0,8)!="MACEBBX1"||BitConverter.ToInt32(h,8)!=1||BitConverter.ToInt32(h,12)!=64||BitConverter.ToInt32(h,16)!=192||BitConverter.ToInt32(h,44)!=actor.Jvm.Id||BitConverter.ToUInt32(h,56)!=Crc(h,56)) continue;
                    if(((BitConverter.ToInt32(h,40)&2)!=0)!=actor.Simulated) continue;
                    long created=BitConverter.ToInt64(h,24);
                    long born=(actor.Jvm.StartTime.ToUniversalTime().Ticks-621355968000000000L)/10000;
                    if(created<born-1000) continue;
                    if(created>bestCreated || (created==bestCreated && String.CompareOrdinal(path,best)>0)) { best=path; bestCreated=created; }
                }} catch { result.Status="IO_OR_PARTIAL"; }
            }
            if(best==null) return result;
            try { using(FileStream stream=new FileStream(best,FileMode.Open,FileAccess.Read,FileShare.ReadWrite|FileShare.Delete)) {
                byte[] h=new byte[64]; if(stream.Read(h,0,64)!=64||BitConverter.ToUInt32(h,56)!=Crc(h,56)||BitConverter.ToInt32(h,44)!=actor.Jvm.Id) {result.Status="UNSTABLE";return result;}
                result.Status="OK";
                byte[] record=new byte[192]; long sequence=1; int limit=65536;
                while(limit-->0) {
                    int read=stream.Read(record,0,192); if(read==0) break;
                    if(read!=192 || BitConverter.ToUInt32(record,176)!=Crc(record,176)||BitConverter.ToInt64(record,0)!=sequence++) {result.Status="PARTIAL_TAIL";break;}
                    int kind=BitConverter.ToInt32(record,8), size=BitConverter.ToInt32(record,12);
                    long wall=BitConverter.ToInt64(record,32);
                    if((kind!=1&&kind!=2)||BitConverter.ToInt32(record,52)!=2) continue;
                    if(kind==1&&size==56&&BitConverter.ToInt32(record,48)==56) {
                        result.Cpu="cpu_provider="+BitConverter.ToInt32(record,68)+" cpu_t="+BitConverter.ToInt32(record,76)+" cpu_p="+BitConverter.ToInt32(record,84)+" cpu_u="+BitConverter.ToInt32(record,92);
                        result.CpuWall=wall; result.CpuStamp=BitConverter.ToInt64(record,56);
                    }
                    if(kind==2&&size==104&&BitConverter.ToInt32(record,48)==104) {
                        string key=BitConverter.ToUInt64(record,64).ToString(); int label;
                        if(!actor.GpuLabels.TryGetValue(key,out label)) {label=actor.GpuLabels.Count+1;actor.GpuLabels[key]=label;}
                        string prefix="g"+label;
                        result.Gpus[key]=prefix+"_provider="+BitConverter.ToInt32(record,72)+" "+prefix+"_t="+BitConverter.ToInt32(record,84)+" "+prefix+"_p="+BitConverter.ToInt32(record,92)+" "+prefix+"_u="+BitConverter.ToInt32(record,100)+" "+prefix+"_fan="+BitConverter.ToInt32(record,108)+" "+prefix+"_mem="+BitConverter.ToInt32(record,128);
                        result.GpuWall[key]=wall;
                    }
                    result.LatestWall=Math.Max(result.LatestWall,wall);
                }
            }} catch { result.Status="IO_OR_PARTIAL"; }
            long now=(DateTime.UtcNow.Ticks-621355968000000000L)/10000;
            foreach(KeyValuePair<string,string> gpu in result.Gpus) { long age=now-result.GpuWall[gpu.Key]; result.Gpu+=" "+gpu.Value+" g"+actor.GpuLabels[gpu.Key]+"_recent="+(Alive(actor.Jvm)&&age>=0&&age<=3000?1:0); }
            return result;
        }
        public static void Sample(string log,ManualActor actor,string stage) {
            lock(actor) {
            RefreshHelpers(actor);
            bool root=Alive(actor.Launcher), vm=Alive(actor.Jvm);
            int helperCount=0; foreach(Process helper in actor.Helpers.Values) if(Alive(helper)) helperCount++;
            uint pawn; int error=PawnState(out pawn);
            ulong tick=GetTickCount64(), gap=actor.PreviousTick==0?0:tick-actor.PreviousTick; actor.PreviousTick=tick;
            uint windowOwner=0; if(vm) try { IntPtr window=actor.Jvm.MainWindowHandle; if(window!=IntPtr.Zero) GetWindowThreadProcessId(window,out windowOwner); } catch { }
            V2 data=ReadV2(actor); long now=(DateTime.UtcNow.Ticks-621355968000000000L)/10000;
            long age=data.CpuWall==0?-1:now-data.CpuWall;
            string exit="unknown"; if(actor.Launcher!=null&&!root) try {exit=actor.Launcher.ExitCode.ToString();} catch { }
            string vmExit="unknown"; if(actor.Jvm!=null&&!vm) try {vmExit=actor.Jvm.ExitCode.ToString();} catch { }
            Log(log,"kind=SAMPLE stage="+stage+" root_pid="+actor.RootId+" root_alive="+(root?1:0)+" root_exit="+exit+" jvm_pid="+(actor.Jvm==null?0:actor.Jvm.Id)+" jvm_observed="+(actor.SawJvm?1:0)+" jvm_alive="+(vm?1:0)+" jvm_exit="+vmExit+" window_owner="+windowOwner+" helper_observed="+(actor.SawHelper?1:0)+" helper_query_ok="+(actor.HelperQueryOk?1:0)+" helper_alive="+helperCount+" helper_pids="+HelperIds(actor)+" image_helper_count="+ImageHelperCount(actor.Helper)+" pawnio="+StateName(pawn)+" pawnio_error="+error+" monitor_gap_ms="+gap+" bb="+data.Status+" bb_source=JVM_PERSISTED_V2 cpu_recent="+(vm&&age>=0&&age<=3000?1:0)+" cpu_record_age_ms="+age+" cpu_stamp_ns="+data.CpuStamp+" "+data.Cpu+data.Gpu);
            }
        }
        // Direct .NET calls/file logging in finally: no pipeline output required after Ctrl+C.
        public static bool RestorePawnRunning(string log,ManualActor actor,int seconds) {
            DateTime deadline=DateTime.UtcNow.AddSeconds(seconds);
            while(DateTime.UtcNow<deadline) {
                try {
                    uint state; int error=PawnState(out state);
                    if(error==0&&state==4) {BestLog(log,"kind=RESTORE_END running=1");return true;}
                    if(error==1060) break; // Never install a missing service.
                    // Request restoration before any telemetry/log I/O.
                    if(error==0&&state==1) { int start=StartPawn(out state); BestLog(log,"kind=RESTORE_START win32="+start); }
                    Sample(log,actor,"RESTORE_FINALLY");
                } catch { BestLog(log,"kind=RESTORE_SAMPLE_UNAVAILABLE"); }
                Thread.Sleep(1000);
            }
            BestLog(log,"kind=NO_VERIFICADO reason=RESTORE_RUNNING_NOT_CONFIRMED"); return false;
        }
    }
}
