// Modified by nmdft on 2026-10-17; see NOTICE.md for fork changes and license.
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.security.MessageDigest;
import android.system.Os;
import android.system.OsConstants;
import java.io.*;
import java.nio.*;

/** ADB shell UHID broker, bound to loopback and authenticated with an app-private random token. */
public final class KeyboardServer {
    // Keyboard bitmap: usages 0..231, including all eight modifier keys. Report 2: consumer keys.
    private static volatile int leds;
    static final byte[] DESCRIPTOR = hex(
        "05010906a10185010507190029e715002501750195e88102" +
        "050819012905950575019102950175039103c0" +
        "050c0901a1018502150026ff0319002aff03751095018100c0");
    static byte[] hex(String s) {
        byte[] b = new byte[s.length()/2];
        for (int i=0;i<b.length;i++) b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);
        return b;
    }
    static void write(FileDescriptor fd, byte[] data) throws Exception {
        if (Os.write(fd, data, 0, data.length) != data.length) throw new IOException("short UHID write");
    }
    static void report(FileDescriptor fd, byte[] report) throws Exception {
        ByteBuffer b=ByteBuffer.allocate(6+report.length).order(ByteOrder.nativeOrder());
        b.putInt(12).putShort((short)report.length).put(report); write(fd,b.array());
    }
    public static void main(String[] args) throws Exception {
        if(args.length!=1) throw new IllegalArgumentException("authentication-token");
        // Closing the socket connection destroys the keyboard, releasing every held key.
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))) {
            System.out.println("READY " + args[0] + " " + server.getLocalPort()); System.out.flush();
            while(true) {
                try(Socket client=server.accept()) {
                    client.setSoTimeout(3000);
                    byte[] expected=args[0].getBytes("US-ASCII");
                    byte[] supplied=new byte[expected.length];
                    new DataInputStream(client.getInputStream()).readFully(supplied);
                    if(!MessageDigest.isEqual(expected,supplied)) continue;
                    client.setSoTimeout(0);
                    FileDescriptor fd=Os.open("/dev/uhid",OsConstants.O_RDWR,0);
                    try {
                        ByteBuffer create=ByteBuffer.allocate(280+DESCRIPTOR.length).order(ByteOrder.nativeOrder());
                        create.putInt(11).put("Deskflow Physical Keyboard".getBytes("UTF-8"));
                        create.position(260);
                        create.putShort((short)DESCRIPTOR.length).putShort((short)6);
                        create.putInt(0x1209).putInt(0xd35f).putInt(1).putInt(0).put(DESCRIPTOR);
                        write(fd,create.array());
                        // Drain device output (LED updates); never let the kernel event queue fill.
                        Thread drain=new Thread(() -> {
                            byte[] event=new byte[4380];
                            try {
                                int n;
                                while((n=Os.read(fd,event,0,event.length))>0) {
                                    if(n>=4103 && ByteBuffer.wrap(event).order(ByteOrder.nativeOrder()).getInt()==6) {
                                        int size=ByteBuffer.wrap(event).order(ByteOrder.nativeOrder()).getShort(4100)&65535;
                                        if(size==2 && event[4]==1) leds=event[5]&7;
                                    }
                                }
                            } catch(Exception ignored) {}
                        },"uhid-output"); drain.setDaemon(true);drain.start();
                        Thread.sleep(150); // Allow Android to report initial lock LEDs.
                        client.getOutputStream().write(1);client.getOutputStream().write(leds);client.getOutputStream().flush();
                        DataInputStream input=new DataInputStream(client.getInputStream());
                        while(true) {
                            int length=input.readUnsignedByte();
                            if(length!=30 && length!=3) throw new IOException("invalid report length");
                            byte[] data=new byte[length]; input.readFully(data);
                            if((length==30 && data[0]!=1)||(length==3 && data[0]!=2)) throw new IOException("invalid report id");
                            report(fd,data);
                        }
                    } finally { Os.close(fd); }
                } catch(Exception e) { System.err.println("Keyboard connection ended: "+e); }
            }
        }
    }
}
