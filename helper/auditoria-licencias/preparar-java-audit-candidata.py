"""External test-only overlays. Keeps all assertions and tests, changes only ownership audit.
Does not modify source checkout or any main/product class; no tests executed here.
"""
import os
from candidata_contexto import *

AUDIT=ROOT/'test-audit';java=AUDIT/'java/com/mace/testaudit';java.mkdir(parents=True,exist_ok=True)
body=r'''
package com.mace.testaudit;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
public final class CandidateAudit {
 private static final Map<Long,Long> owned=new ConcurrentHashMap<>();
 private static Path dir(){ Path d=Path.of(System.getenv("TEMP"));if(!Files.isRegularFile(d.resolve(".mace-candidate-process-audit")))throw new IllegalStateException("AUDIT_MARKER_MISSING");return d; }
 public static synchronized void record(String event,ProcessHandle p,long parent) {
  long born=p.info().startInstant().orElseThrow(()->new IllegalStateException("CREATION_TIME_MISSING")).toEpochMilli();
  if(event.equals("CREATE")||event.equals("DESCENDANT"))owned.put(p.pid(),born);
  String row=event+" "+p.pid()+" "+born+" "+parent+" "+ProcessHandle.current().pid()+"\n";
  try { Files.writeString(dir().resolve("p_"+p.pid()+"_"+born+".audit"),row,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    String context=StackWalker.getInstance().walk(s->s.filter(f->f.getClassName().endsWith("Test")&&!Set.of("run","launch","death","startHelper").contains(f.getMethodName())).map(f->f.getClassName()+"."+f.getMethodName()).findFirst().orElse("jvm"));
    Files.writeString(dir().resolve("case_"+p.pid()+"_"+born+".txt"),context);
  } catch(java.io.IOException ex){throw new IllegalStateException("AUDIT_WRITE_FAILED");}
 }
 public static void premain(String ignored,java.lang.instrument.Instrumentation instrumentation){record("SELF",ProcessHandle.current(),ProcessHandle.current().parent().map(ProcessHandle::pid).orElse(0L));}
 public static Process start(ProcessBuilder builder)throws java.io.IOException {Process p=builder.start();record("CREATE",p.toHandle(),ProcessHandle.current().pid());return p;}
 public static ProcessHandle descendant(Process parent){
  List<ProcessHandle> list=parent.children().filter(p->Files.isRegularFile(dir().resolve("p_"+p.pid()+"_"+p.info().startInstant().orElseThrow().toEpochMilli()+".audit"))).toList();
  if(list.isEmpty())return null;
  if(list.size()!=1)throw new IllegalStateException("OWNED_DESCENDANT_AMBIGUOUS");
  ProcessHandle p=list.getFirst();record("DESCENDANT",p,parent.pid());return p;
 }
 public static void beforeNativeTermination(Process process){
  if(!process.isAlive())return;
  Long born=owned.get(process.pid());
  if(born==null||process.info().startInstant().orElseThrow().toEpochMilli()!=born)throw new IllegalStateException("OWNERSHIP_MISMATCH");
  record("KILL_REQUEST",process.toHandle(),ProcessHandle.current().pid());
 }
 public static void terminate(Process process){if(!process.isAlive())return;beforeNativeTermination(process);process.destroyForcibly();}
}
'''
(java/'CandidateAudit.java').write_text(body,encoding='utf8')
files={
 'com/mace/presentation/ShutdownWatchdogProcessTest.java':[
 ('Process child = command.start();','Process child = com.mace.testaudit.CandidateAudit.start(command);'),
 ('''owned = child.descendants().filter(process -> process.info().command()
                    .map(name -> Path.of(name).getFileName().toString().equalsIgnoreCase(Path.of(helper).getFileName().toString()))
                    .orElse(false)).findFirst().orElse(null);''','owned = com.mace.testaudit.CandidateAudit.descendant(child);'),
 ('child.destroyForcibly();','com.mace.testaudit.CandidateAudit.terminate(child);')],
 'com/mace/presentation/SingleInstanceProcessTest.java':[
 ('owner.process.destroyForcibly();','com.mace.testaudit.CandidateAudit.terminate(owner.process);'),
 ('return new Probe(builder.start());','return new Probe(com.mace.testaudit.CandidateAudit.start(builder));'),
 ('''var child = owner.process.descendants().filter(p -> p.info().command()
                    .map(c -> Path.of(c).getFileName().toString().equalsIgnoreCase(expected)).orElse(false)).findFirst();
            if (child.isPresent()) return child.get();''','''var child = com.mace.testaudit.CandidateAudit.descendant(owner.process);
            if (child != null) return child;'''),
 ('process.destroyForcibly();','com.mace.testaudit.CandidateAudit.terminate(process);')],
 'com/mace/infrastructure/nativebridge/NativeV2IntegrationTest.java':[
 ('assertEquals(TerminationOutcome.TERMINATED, processes.terminate(identity).outcome());','com.mace.testaudit.CandidateAudit.beforeNativeTermination(helper);\n            assertEquals(TerminationOutcome.TERMINATED, processes.terminate(identity).outcome());'),
 ('helper.destroyForcibly();','com.mace.testaudit.CandidateAudit.terminate(helper);'),
 ('''return new ProcessBuilder(java, source.toString()).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();''','''return com.mace.testaudit.CandidateAudit.start(new ProcessBuilder(java, source.toString()).redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD));''')],
 'com/mace/infrastructure/nativebridge/NativeProductionPolicyTest.java':[
 ('Process child = builder.redirectOutput(output.toFile()).start();','Process child = com.mace.testaudit.CandidateAudit.start(builder.redirectOutput(output.toFile()));')]
}
overlays=[]
for relative,replacements in files.items():
    text=(SOURCE/'app/src/test/java'/relative).read_text(encoding='utf8')
    for old,new in replacements:
        if text.count(old)!=1:raise RuntimeError('OVERLAY_ANCHOR_MISMATCH_'+Path(relative).name)
        text=text.replace(old,new)
    path=AUDIT/'java'/relative;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(text,encoding='utf8');overlays.append(path)
jdk,_=tools()
classes=AUDIT/'classes';classes.mkdir(exist_ok=True)
run('audit-agent-compile',[jdk/'bin/javac.exe','-d',classes,java/'CandidateAudit.java'])
manifest=AUDIT/'MANIFEST.MF';manifest.write_text('Manifest-Version: 1.0\nPremain-Class: com.mace.testaudit.CandidateAudit\n\n',encoding='ascii')
run('audit-agent-jar',[jdk/'bin/jar.exe','--create','--file',AUDIT/'audit-agent.jar','--manifest',manifest,'-C',classes,'.'])
classpath=[SOURCE/'app/target/candidate-development/classes',SOURCE/'app/target/candidate-development/test-classes',classes]
for line in (EVIDENCE/'patched-tree-private.tgf').read_text().splitlines()[1:]:
    if line=='#':break
    parts=line.split()[1].split(':');group,artifact,kind=parts[:3]
    if len(parts)==6:classifier,version,scope=parts[3:]
    else:version,scope=parts[3:];classifier=None
    jar=ROOT/'deps/m2'/group.replace('.','/')/artifact/version/(artifact+'-'+version+('-'+classifier if classifier else '')+'.jar')
    if not jar.is_file():raise RuntimeError('OFFLINE_CLASSPATH_MISSING')
    classpath.append(jar)
args=AUDIT/'javac-private.args'
lines=['-encoding','UTF-8','-cp','"'+os.pathsep.join(str(p) for p in classpath).replace('\\','/')+'"','-d','"'+str(SOURCE/'app/target/candidate-development/test-classes').replace('\\','/')+'"']
lines+=['"'+str(p).replace('\\','/')+'"' for p in [java/'CandidateAudit.java']+overlays]
args.write_text('\n'.join(lines),encoding='utf8')
run('audit-overlays-compile',[jdk/'bin/javac.exe','@'+str(args)])
save('test-audit-design.json',{'overlay_test_classes':4,'test_assertions_removed':0,'main_classes_modified':0,'candidate_git_source_modified':False,'agent_packaged':False})
print('JAVA_AUDIT_READY overlays=4 product_classes_modified=0',flush=True)
