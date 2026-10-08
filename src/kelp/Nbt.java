package kelp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minecraft's NBT format, the way it keeps files like servers.dat: tags with names, nested in compounds and lists.
 * Kelp reads a file into plain Java values and writes it back exactly, so changing one thing (adding a server) keeps
 * everything else in the file as it was, even things Kelp doesn't know about.
 *
 * Values: Byte, Short, Integer, Long, Float, Double, byte[], String, {@link ListTag}, Map (a compound, in order),
 * int[] and long[].
 */
public final class Nbt {
    /** A list, which remembers what kind of tag it holds (so an empty one is written back the same). */
    public record ListTag(int type, List<Object> items) {
    }

    private static final int END = 0, BYTE = 1, SHORT = 2, INT = 3, LONG = 4, FLOAT = 5, DOUBLE = 6, BYTES = 7,
            STRING = 8, LIST = 9, COMPOUND = 10, INTS = 11, LONGS = 12;

    private Nbt() {
    }

    /** Reads an uncompressed NBT file: its root compound. */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> read(byte[] data) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
        int type = in.readUnsignedByte();
        if (type != COMPOUND) throw new IOException("not an NBT file (it doesn't start with a compound)");
        in.readUTF(); // the root's name, which is empty
        return (Map<String, Object>) value(in, COMPOUND, 0);
    }

    /** Writes a root compound as an uncompressed NBT file. */
    public static byte[] write(Map<String, Object> root) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(COMPOUND);
        out.writeUTF("");
        write(out, root);
        out.flush();
        return bytes.toByteArray();
    }

    private static Object value(DataInputStream in, int type, int depth) throws IOException {
        if (depth > 512) throw new IOException("the NBT is nested too deep");
        return switch (type) {
            case BYTE -> in.readByte();
            case SHORT -> in.readShort();
            case INT -> in.readInt();
            case LONG -> in.readLong();
            case FLOAT -> in.readFloat();
            case DOUBLE -> in.readDouble();
            case BYTES -> {
                byte[] b = new byte[length(in)];
                in.readFully(b);
                yield b;
            }
            case STRING -> in.readUTF();
            case LIST -> {
                int itemType = in.readUnsignedByte();
                int count = length(in);
                List<Object> items = new ArrayList<>();
                for (int i = 0; i < count; i++) items.add(value(in, itemType, depth + 1));
                yield new ListTag(itemType, items);
            }
            case COMPOUND -> {
                Map<String, Object> map = new LinkedHashMap<>();
                while (true) {
                    int tag = in.readUnsignedByte();
                    if (tag == END) break;
                    String name = in.readUTF();
                    map.put(name, value(in, tag, depth + 1));
                }
                yield map;
            }
            case INTS -> {
                int[] a = new int[length(in)];
                for (int i = 0; i < a.length; i++) a[i] = in.readInt();
                yield a;
            }
            case LONGS -> {
                long[] a = new long[length(in)];
                for (int i = 0; i < a.length; i++) a[i] = in.readLong();
                yield a;
            }
            default -> throw new IOException("an NBT tag Kelp doesn't know: " + type);
        };
    }

    private static int length(DataInputStream in) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > 16_000_000) throw new IOException("a broken NBT length: " + n);
        return n;
    }

    static int typeOf(Object value) {
        if (value instanceof Byte) return BYTE;
        if (value instanceof Short) return SHORT;
        if (value instanceof Integer) return INT;
        if (value instanceof Long) return LONG;
        if (value instanceof Float) return FLOAT;
        if (value instanceof Double) return DOUBLE;
        if (value instanceof byte[]) return BYTES;
        if (value instanceof String) return STRING;
        if (value instanceof ListTag) return LIST;
        if (value instanceof Map) return COMPOUND;
        if (value instanceof int[]) return INTS;
        if (value instanceof long[]) return LONGS;
        throw new IllegalArgumentException("not an NBT value: " + value);
    }

    @SuppressWarnings("unchecked")
    private static void write(DataOutputStream out, Object value) throws IOException {
        switch (value) {
            case Byte b -> out.writeByte(b);
            case Short s -> out.writeShort(s);
            case Integer i -> out.writeInt(i);
            case Long l -> out.writeLong(l);
            case Float f -> out.writeFloat(f);
            case Double d -> out.writeDouble(d);
            case byte[] b -> {
                out.writeInt(b.length);
                out.write(b);
            }
            case String s -> out.writeUTF(s);
            case ListTag list -> {
                out.writeByte(list.items().isEmpty() ? list.type() : typeOf(list.items().getFirst()));
                out.writeInt(list.items().size());
                for (Object item : list.items()) write(out, item);
            }
            case Map<?, ?> map -> {
                for (Map.Entry<String, Object> e : ((Map<String, Object>) map).entrySet()) {
                    out.writeByte(typeOf(e.getValue()));
                    out.writeUTF(e.getKey());
                    write(out, e.getValue());
                }
                out.writeByte(END);
            }
            case int[] a -> {
                out.writeInt(a.length);
                for (int i : a) out.writeInt(i);
            }
            case long[] a -> {
                out.writeInt(a.length);
                for (long l : a) out.writeLong(l);
            }
            default -> throw new IllegalArgumentException("not an NBT value: " + value);
        }
    }
}
