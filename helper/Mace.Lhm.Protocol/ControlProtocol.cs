using System.Buffers.Binary;

namespace Mace.Lhm.Protocol;

/// <summary>One shutdown command per authenticated duplex connection.</summary>
public static class ControlProtocol
{
    public const uint Magic = 0x3143434D; // MCC1
    public const int Size = 32;
    public const int FrameDeadlineMs = 500;
    public const int DuplicateDrainMs = 100;

    public static byte[] Shutdown(uint senderPid)
    {
        if (senderPid == 0) throw new ArgumentException("Missing control PID.");
        byte[] frame = new byte[Size];
        BinaryPrimitives.WriteUInt32LittleEndian(frame, Magic);
        BinaryPrimitives.WriteUInt16LittleEndian(frame.AsSpan(4), WireProtocol.Version);
        BinaryPrimitives.WriteUInt16LittleEndian(frame.AsSpan(6), Size);
        BinaryPrimitives.WriteUInt32LittleEndian(frame.AsSpan(8), Size);
        BinaryPrimitives.WriteUInt32LittleEndian(frame.AsSpan(12), 1); // Shutdown, the only command.
        BinaryPrimitives.WriteUInt64LittleEndian(frame.AsSpan(16), 1);
        BinaryPrimitives.WriteUInt32LittleEndian(frame.AsSpan(24), senderPid);
        return frame;
    }

    public static ulong Accept(byte[] frame, uint expectedPid, ulong previousSequence)
    {
        if (frame.Length != Size || BinaryPrimitives.ReadUInt32LittleEndian(frame) != Magic
            || BinaryPrimitives.ReadUInt16LittleEndian(frame.AsSpan(4)) != WireProtocol.Version
            || BinaryPrimitives.ReadUInt16LittleEndian(frame.AsSpan(6)) != Size
            || BinaryPrimitives.ReadUInt32LittleEndian(frame.AsSpan(8)) != Size
            || BinaryPrimitives.ReadUInt32LittleEndian(frame.AsSpan(12)) != 1
            || BinaryPrimitives.ReadUInt64LittleEndian(frame.AsSpan(16)) != 1
            || previousSequence != 0 || expectedPid == 0
            || BinaryPrimitives.ReadUInt32LittleEndian(frame.AsSpan(24)) != expectedPid
            || BinaryPrimitives.ReadUInt32LittleEndian(frame.AsSpan(28)) != 0)
            throw new InvalidDataException("Invalid or repeated shutdown control.");
        return 1;
    }

    private static async Task<byte[]?> ReadAsync(Stream stream, CancellationToken idle)
    {
        byte[] frame = new byte[Size];
        if (await stream.ReadAsync(frame.AsMemory(0, 1), idle) == 0) return null;
        using var deadline = CancellationTokenSource.CreateLinkedTokenSource(idle);
        deadline.CancelAfter(FrameDeadlineMs);
        try { await stream.ReadExactlyAsync(frame.AsMemory(1), deadline.Token); }
        catch (OperationCanceledException) { throw new InvalidDataException("Incomplete control deadline."); }
        return frame;
    }

    public static async Task ListenAsync(Stream stream, uint expectedPid, Action requestShutdown,
        CancellationToken lifetime)
    {
        ulong sequence = 0;
        CancellationTokenSource? drain = null;
        try
        {
            while (true)
            {
                byte[]? frame;
                try { frame = await ReadAsync(stream, drain?.Token ?? lifetime); }
                catch (OperationCanceledException) when (sequence != 0 && !lifetime.IsCancellationRequested)
                { return; } // Short bounded drain; shutdown never depends on receiving EOF.
                if (frame is null) { requestShutdown(); return; } // EOF remains an independent fallback.
                sequence = Accept(frame, expectedPid, sequence);
                requestShutdown();
                drain = CancellationTokenSource.CreateLinkedTokenSource(lifetime);
                drain.CancelAfter(DuplicateDrainMs);
            }
        }
        catch (OperationCanceledException) when (lifetime.IsCancellationRequested) { }
        catch (IOException error) when ((error.HResult & 0xFFFF) is 109 or 232 or 233)
        { requestShutdown(); } // Named-pipe EOF can be surfaced as a broken-pipe error.
        catch { requestShutdown(); throw; }
        finally { drain?.Dispose(); }
    }
}
