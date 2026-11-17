using System.Buffers.Binary;
using System.Text;

namespace Mace.Lhm.Protocol;

// Fixed-size private startup block through an inherited anonymous stdin pipe.
public static class Bootstrap
{
    public static (string PipeName, uint ParentPid) Read(Stream input)
    {
        byte[] data=new byte[128];
        input.ReadExactly(data);
        if (BinaryPrimitives.ReadUInt32LittleEndian(data)!=0x3142434D
            || BinaryPrimitives.ReadUInt32LittleEndian(data.AsSpan(4))!=WireProtocol.Version
            || BinaryPrimitives.ReadUInt32LittleEndian(data.AsSpan(12))!=73
            || data.AsSpan(89).ContainsAnyExcept((byte)0))
            throw new InvalidDataException("Invalid bootstrap.");
        uint parent=BinaryPrimitives.ReadUInt32LittleEndian(data.AsSpan(8));
        if (parent==0) throw new InvalidDataException("Missing parent PID.");
        string name=Encoding.ASCII.GetString(data,16,73);
        PrivatePipe.ValidateName(name);
        Array.Clear(data);
        return (name,parent);
    }
}
