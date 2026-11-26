"""CTest target launcher, recording actual child identity before waiting. No kill."""
import subprocess
import sys
from procesos_candidata import self_record,register
self_record()
child=subprocess.Popen(sys.argv[1:])
register(child)
sys.exit(child.wait())
